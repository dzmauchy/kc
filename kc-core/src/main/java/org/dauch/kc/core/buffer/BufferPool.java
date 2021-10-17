/*
 * Copyright 2021 Dzmiter Auchynnikau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dauch.kc.core.buffer;

import org.dauch.kc.core.client.KafkaClientProperties;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.rmi.server.UID;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Thread.currentThread;
import static java.nio.channels.FileChannel.MapMode.PRIVATE;
import static java.nio.file.StandardOpenOption.*;
import static java.util.Objects.requireNonNull;

public final class BufferPool implements AutoCloseable {

  private static final ThreadGroup THREAD_GROUP = new ThreadGroup("bufferPools");
  private static final EnumSet<StandardOpenOption> CUSTOM_OPEN_OPTS = EnumSet.of(CREATE_NEW, WRITE, SPARSE);

  private final BufferConfig bufferConfig;
  private final ReferenceQueue<DataBuffer> refQueue = new ReferenceQueue<>();
  private final ConcurrentSkipListMap<Integer, ConcurrentLinkedQueue<DataBuffer>> bufs = new ConcurrentSkipListMap<>();
  private final IdentityHashMap<ByteBuffer, BufferDesc> descs = new IdentityHashMap<>(1024);
  private final ConcurrentHashMap<PhantomReference<DataBuffer>, ByteBuffer> refs = new ConcurrentHashMap<>(64, 0.5f);
  private final AtomicLong actualSize = new AtomicLong();
  private final Thread thread;

  public BufferPool(String clientId, KafkaClientProperties props) {
    this.bufferConfig = BufferConfig.fromString(requireNonNull(props, "props is null").getBufferConfig());
    this.thread = new Thread(THREAD_GROUP, this::clean, clientId + "_cleaner", 128L << 10);
    this.thread.setDaemon(true);
    this.thread.setPriority(Thread.MIN_PRIORITY);
    this.thread.start();
  }

  public DataBuffer get(int size, InputStream is) throws IOException {
    if (size <= 0) {
      throw new IllegalArgumentException("Invalid size: " + size);
    }
    if (bufferConfig.type() == BufferType.HEAP) {
      return new DataBuffer(ByteBuffer.wrap(is.readNBytes(size)));
    }
    return get0(size, is, bufferConfig);
  }

  static void transferNBytes(int size, InputStream is, ByteBuffer buf) throws IOException {
    buf.clear();
    var data = new byte[1024];
    var len = size;
    while (len > 0) {
      var n = is.read(data, 0, Math.min(len, 512));
      if (n < 0) {
        break;
      }
      len -= n;
      buf.put(data, 0, n);
    }
    buf.flip();
  }

  private DataBuffer obtainDataBuffer(ByteBuffer rawBuf) {
    var buf = new DataBuffer(rawBuf);
    var ref = new PhantomReference<>(buf, refQueue);
    refs.put(ref, rawBuf);
    return buf;
  }

  private void registerDesc(ByteBuffer rawBuf, BufferDesc desc) {
    synchronized (descs) {
      descs.put(rawBuf, desc);
    }
  }

  private DataBuffer get0(int size, InputStream is, BufferConfig config) throws IOException {
    var subMap = bufs.tailMap(size, true);
    var e = subMap.pollFirstEntry();
    if (e == null) {
      switch (config.type()) {
        case HEAP: {
          return new DataBuffer(ByteBuffer.wrap(is.readNBytes(size)));
        }
        case DIRECT: {
          var conf = (DirectBufferConfig) config;
          var totalSize = actualSize.addAndGet(size);
          if (totalSize > conf.maxSize) {
            actualSize.addAndGet(-size);
            return get0(size, is, new HeapBufferConfig());
          }
          var rawBuf = ByteBuffer.allocateDirect(size);
          transferNBytes(size, is, rawBuf);
          var buf = obtainDataBuffer(rawBuf);
          registerDesc(rawBuf, new BufferDesc());
          return buf;
        }
        case MMAP: {
          var conf = (MappedBufferConfig) config;
          var totalSize = actualSize.addAndGet(size);
          if (totalSize > conf.maxSize) {
            actualSize.addAndGet(-size);
            return get0(size, is, new HeapBufferConfig());
          }
          var uid = new UID().toString().replace(':', '_');
          var file = conf.directory.resolve("kc-" + uid + ".kctmp");
          try (var ch = FileChannel.open(file, CUSTOM_OPEN_OPTS)) {
            var rawBuf = ch.map(PRIVATE, 0L, size);
            transferNBytes(size, is, rawBuf);
            var buf = obtainDataBuffer(rawBuf);
            registerDesc(rawBuf, new BufferDesc() {
              @Override
              void clean() throws IOException {
                Files.deleteIfExists(file);
              }
            });
            return buf;
          }
        }
        default:
          throw new IllegalStateException("Unknown type: " + config.type());
      }
    } else {
      var buffers = e.getValue();
      var buf = buffers.poll();
      if (buf == null) {
        buffers = bufs.remove(e.getKey());
        if (buffers == null || (buf = buffers.poll()) == null) {
          return get0(size, is, config);
        } else if (!buffers.isEmpty()) {
          var newBuffers = buffers;
          bufs.computeIfPresent(e.getKey(), (k, o) -> {
            o.addAll(newBuffers);
            return o;
          });
        }
      }
      var rawBuf = buf.buffer;
      transferNBytes(size, is, rawBuf);
      if (config instanceof MappedBufferConfig) {
        for (int i = rawBuf.limit(); i < rawBuf.capacity(); i++) {
          rawBuf.put(i, (byte) 0);
        }
      }
      return buf;
    }
  }

  private void clean() {
    while (!currentThread().isInterrupted()) {
      for (var ref = refQueue.poll(); ref != null; ref = refQueue.poll()) {
        //noinspection SuspiciousMethodCalls
        var rawBuf = refs.remove(ref);
        final BufferDesc desc;
        synchronized (descs) {
          desc = descs.get(rawBuf);
        }

      }
    }
  }

  @Override
  public void close() throws Exception {
    thread.interrupt();
    thread.join();
  }

  private static class BufferDesc {
    void clean() throws IOException {
    }
  }
}
