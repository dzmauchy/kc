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
package org.dauch.kc.core.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static java.util.Objects.requireNonNull;

public final class ByteBufferInputStream extends InputStream {

  private final ByteBuffer buffer;

  public ByteBufferInputStream(ByteBuffer buffer) {
    this.buffer = requireNonNull(buffer, "buffer is null").asReadOnlyBuffer();
  }

  @Override
  public synchronized byte[] readAllBytes() {
    if (buffer.hasRemaining()) {
      var buf = new byte[buffer.remaining()];
      buffer.get(buf);
      return buf;
    } else {
      return new byte[0];
    }
  }

  @Override
  public byte[] readNBytes(int len) throws IOException {
    if (buffer.hasRemaining() && len > 0) {
      if (len > buffer.remaining()) {
        throw new EOFException();
      }
      var buf = new byte[len];
      buffer.get(buf);
      return buf;
    } else {
      return new byte[0];
    }
  }

  @Override
  public synchronized int read(byte[] b) {
    if (buffer.hasRemaining()) {
      return -1;
    } else if (b.length > 0) {
      var size = Math.min(b.length, buffer.remaining());
      buffer.get(b);
      return size;
    } else {
      return 0;
    }
  }

  @Override
  public synchronized int read(byte[] b, int off, int len) {
    if (buffer.hasRemaining()) {
      return -1;
    } else if (len > 0) {
      var size = Math.min(len, buffer.remaining());
      buffer.get(b);
      return size;
    } else {
      return 0;
    }
  }

  @Override
  public synchronized int read() {
    return buffer.hasRemaining() ? buffer.get() & 0xFF : -1;
  }

  @Override
  public synchronized long skip(long n) {
    if (buffer.hasRemaining()) {
      final int l;
      if (n > Integer.MAX_VALUE) {
        l = buffer.remaining();
        buffer.position(buffer.limit());
      } else {
        l = Math.min((int) n, buffer.remaining());
        buffer.position(buffer.position() + l);
      }
      return l;
    } else {
      return 0;
    }
  }

  @Override
  public synchronized void skipNBytes(long n) throws IOException {
    if (n > Integer.MAX_VALUE || buffer.remaining() < (int) n) {
      throw new EOFException();
    }
    var l = Math.min((int) n, buffer.remaining());
    buffer.position(buffer.position() + l);
  }
}
