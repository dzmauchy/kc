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

import org.dauch.kc.core.util.Size;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

interface BufferConfig {

  BufferType type();

  static BufferConfig fromString(String config) {
    var conf = requireNonNullElse(config, "HEAP");
    var parts = conf.split("&");
    var type = bufferType(parts[0]);
    switch (type) {
      case HEAP:
        return new HeapBufferConfig();
      case MMAP: {
        var maxSize = 1L << 30;
        var size = 1024;
        var dir = MappedBufferConfig.DEFAULT_DIRECTORY;
        for (var kv : kvs(conf, parts)) {
          switch (kv[0]) {
            case "size":
              try {
                size = Size.parse(kv[1]).toInt();
              } catch (Throwable e) {
                throw new IllegalArgumentException("Invalid size in " + conf, e);
              }
              break;
            case "maxSize":
              try {
                maxSize = Size.parse(kv[1]).toLong();
              } catch (Throwable e) {
                throw new IllegalArgumentException("Invalid maxSize in " + conf, e);
              }
              break;
            case "dir":
              try {
                dir = Path.of(kv[1]);
              } catch (Throwable e) {
                throw new IllegalArgumentException("Invalid dir in " + conf, e);
              }
              break;
          }
        }
        return new MappedBufferConfig(dir, size, maxSize);
      }
      case DIRECT: {
        var maxSize = 10L << 20;
        for (var kv: kvs(conf, parts)) {
          switch (kv[0]) {
            case "maxSize":
              try {
                maxSize = Size.parse(kv[1]).toLong();
              } catch (Throwable e) {
                throw new IllegalArgumentException("Invalid size in " + conf, e);
              }
              break;
          }
        }
        return new DirectBufferConfig(maxSize);
      }
      default:
        throw new IllegalStateException("Unknown type: " + type);
    }
  }

  private static BufferType bufferType(String type) {
    switch (type.trim().toUpperCase()) {
      case "HEAP": return BufferType.HEAP;
      case "MMAP": return BufferType.MMAP;
      case "DIRECT": return BufferType.DIRECT;
      default: throw new IllegalArgumentException("Unknown buffer type: " + type);
    }
  }

  private static Iterable<String[]> kvs(String conf, String[] parts) {
    return () -> new Iterator<>() {

      private int index = 1;

      @Override
      public boolean hasNext() {
        return index < parts.length - 1;
      }

      @Override
      public String[] next() {
        if (hasNext()) {
          var kv = parts[index].trim();
          var idx = kv.indexOf('=');
          if (idx < 0) {
            throw new IllegalArgumentException("Invalid buffer config: " + conf);
          }
          var key = kv.substring(0, idx).trim();
          var value = kv.substring(idx + 1).trim();
          index++;
          return new String[]{key, value};
        } else {
          throw new NoSuchElementException();
        }
      }
    };
  }
}

final class HeapBufferConfig implements BufferConfig {
  @Override
  public BufferType type() {
    return BufferType.HEAP;
  }
}

final class DirectBufferConfig implements BufferConfig {

  final long maxSize;

  DirectBufferConfig(long maxSize) {
    this.maxSize = maxSize;
  }

  @Override
  public BufferType type() {
    return BufferType.DIRECT;
  }
}

final class MappedBufferConfig implements BufferConfig {

  static final Path DEFAULT_DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"));

  final Path directory;
  final int sizeThreshold;
  final long maxSize;

  MappedBufferConfig(Path directory, int sizeThreshold, long maxSize) {
    this.directory = requireNonNull(directory, "directory is null");
    this.sizeThreshold = sizeThreshold;
    this.maxSize = maxSize;
  }

  @Override
  public BufferType type() {
    return BufferType.MMAP;
  }
}
