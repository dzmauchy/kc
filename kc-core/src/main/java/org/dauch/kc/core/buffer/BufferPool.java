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
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Character.MAX_RADIX;
import static java.lang.Integer.toUnsignedString;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.currentThread;
import static java.nio.channels.FileChannel.MapMode.PRIVATE;
import static java.nio.file.StandardOpenOption.*;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.locks.LockSupport.parkNanos;

public final class BufferPool implements AutoCloseable {

  private static final System.Logger LOGGER = System.getLogger(BufferPool.class.getName());
  private static final ThreadGroup THREAD_GROUP = new ThreadGroup("bufferPools");
  private static final EnumSet<StandardOpenOption> CUSTOM_OPEN_OPTS = EnumSet.of(CREATE_NEW, WRITE, SPARSE);
  private static final IdentityHashMap<ByteBuffer, Path> FILES = new IdentityHashMap<>(1024);

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      synchronized (FILES) {
        FILES.forEach((k, v) -> {
          try {
            Files.deleteIfExists(v);
          } catch (Throwable x) {
            LOGGER.log(ERROR, () -> "Unable to clean buffer", x);
          }
        });
      }
    }));
  }

  private final BufferConfig bufferConfig;
  private final long maxSize;
  private final ReferenceQueue<DataBuffer> refQueue = new ReferenceQueue<>();
  private final ConcurrentSkipListMap<Integer, ConcurrentLinkedQueue<DataBuffer>> bufs = new ConcurrentSkipListMap<>();
  private final ConcurrentHashMap<Reference<? extends DataBuffer>, ByteBuffer> refs = new ConcurrentHashMap<>(64, 0.5f);
  private final AtomicLong actualSize = new AtomicLong();
  private final AtomicBoolean started = new AtomicBoolean();
  private final Thread thread;

  public BufferPool(String clientId, KafkaClientProperties props) {
    this.bufferConfig = BufferConfig.fromString(requireNonNull(props, "props is null").getBufferConfig());
    this.maxSize = maxSize(bufferConfig);
    this.thread = new Thread(THREAD_GROUP, this::clean, clientId + "_cleaner", 128L << 10);
    this.thread.setDaemon(true);
    this.thread.setPriority(Thread.MIN_PRIORITY);
  }

  public DataBuffer get(int size, InputStream is) throws IOException {
    if (size <= 0) {
      throw new IllegalArgumentException("Invalid size: " + size);
    }
    if (started.compareAndSet(false, true)) {
      thread.start();
    }
    if (bufferConfig.type() == BufferType.HEAP) {
      return getHeapBuffer(size, is);
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

  private static long maxSize(BufferConfig bufferConfig) {
    switch (bufferConfig.type()) {
      case HEAP: return Long.MAX_VALUE;
      case MMAP: return ((MappedBufferConfig) bufferConfig).maxSize;
      case DIRECT: return ((DirectBufferConfig) bufferConfig).maxSize;
      default: throw new IllegalStateException("Unsupported type: " + bufferConfig.type());
    }
  }

  private DataBuffer getHeapBuffer(int size, InputStream is) throws IOException {
    var rawBuf = ByteBuffer.wrap(is.readNBytes(size));
    var buf = new DataBuffer(rawBuf);
    refs.put(new PhantomReference<>(buf, refQueue), rawBuf);
    actualSize.addAndGet(size);
    return buf;
  }

  private DataBuffer get0(int size, InputStream is, BufferConfig config) throws IOException {
    var subMap = bufs.tailMap(size, true);
    var e = subMap.pollFirstEntry();
    if (e == null) {
      switch (config.type()) {
        case HEAP: return getHeapBuffer(size, is);
        case DIRECT: {
          var conf = (DirectBufferConfig) config;
          var totalSize = actualSize.addAndGet(size);
          if (totalSize > conf.maxSize) {
            actualSize.addAndGet(-size);
            return get0(size, is, new HeapBufferConfig());
          }
          var rawBuf = ByteBuffer.allocateDirect(size);
          transferNBytes(size, is, rawBuf);
          return obtainDataBuffer(rawBuf);
        }
        case MMAP: {
          var conf = (MappedBufferConfig) config;
          var totalSize = actualSize.addAndGet(size);
          if (totalSize > conf.maxSize) {
            actualSize.addAndGet(-size);
            return get0(size, is, new HeapBufferConfig());
          }
          var random = toUnsignedString(ThreadLocalRandom.current().nextInt(), MAX_RADIX);
          var time = Long.toString(currentTimeMillis(), MAX_RADIX);
          var nanoTime = toUnsignedString((int) Math.abs(System.nanoTime() % 1_000_000), MAX_RADIX);
          var file = conf.directory.resolve("kc-" + random + "-" + time + "-" + nanoTime + ".kctmp");
          try (var ch = FileChannel.open(file, CUSTOM_OPEN_OPTS)) {
            var rawBuf = ch.map(PRIVATE, 0L, size);
            transferNBytes(size, is, rawBuf);
            var buf = obtainDataBuffer(rawBuf);
            synchronized (FILES) {
              FILES.put(rawBuf, file);
            }
            return buf;
          }
        }
        default: throw new IllegalStateException("Unknown type: " + config.type());
      }
    } else {
      var buffers = e.getValue();
      var buf = buffers.poll();
      if (buf == null) {
        buffers = bufs.remove(e.getKey());
        if (buffers != null) {
          buffers.removeIf(b -> clean(b.buffer));
        }
        return get0(size, is, config);
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

  private boolean clean(ByteBuffer buf) {
    actualSize.addAndGet(-buf.capacity());
    final Path file;
    synchronized (FILES) {
      file = FILES.remove(buf);
    }
    if (file != null) {
      try {
        Files.deleteIfExists(file);
      } catch (Throwable x) {
        LOGGER.log(ERROR, () -> "Unable to clean buffer", x);
      }
    }
    return true;
  }

  private boolean clean0(float cleanFactor) {
    var result = false;
    var direct = bufferConfig.type() != BufferType.HEAP;
    var refList = new LinkedList<Reference<? extends DataBuffer>>();
    for (var ref = refQueue.poll(); ref != null; ref = refQueue.poll()) {
      refList.add(ref);
    }
    for (var ref : refList) {
      var rawBuf = refs.remove(ref);
      if (rawBuf != null && direct) {
        var buf = new DataBuffer(rawBuf);
        var newRef = new PhantomReference<>(buf, refQueue);
        refs.put(newRef, rawBuf);
        bufs.compute(rawBuf.capacity(), (k, o) -> {
          if (o == null) {
            return new ConcurrentLinkedQueue<>(singletonList(buf));
          } else {
            o.add(buf);
            return o;
          }
        });
      }
    }
    if (direct) {
      while (actualSize.floatValue() > cleanFactor * maxSize) {
        result = true;
        var fe = bufs.firstEntry();
        var le = bufs.lastEntry();
        if (fe == null || le == null) {
          var e = bufs.pollLastEntry();
          if (e != null) {
            var buffers = e.getValue();
            buffers.removeIf(b -> clean(b.buffer));
          }
        } else {
          var rnd = ThreadLocalRandom.current().nextInt(fe.getKey(), le.getKey() + 1);
          var e = bufs.tailMap(rnd, true).pollFirstEntry();
          if (e == null) {
            e = bufs.pollLastEntry();
          }
          if (e != null) {
            var buffers = e.getValue();
            buffers.removeIf(b -> clean(b.buffer));
          }
        }
      }
    }
    return result;
  }

  private void clean() {
    while (!currentThread().isInterrupted()) {
      if (!clean0(0.75f)) {
        parkNanos(10_000_000L);
      }
    }
  }

  public long actualSize() {
    return actualSize.get();
  }

  public static int openFilesCount() {
    synchronized (FILES) {
      return FILES.size();
    }
  }

  @Override
  public void close() throws Exception {
    thread.interrupt();
    thread.join();
    System.gc();
    clean0(0.0f);
  }
}
