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
package org.dauch.kc.core;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class ByteBufferByteSequence implements ByteSequence {

  private final ByteBuffer buffer;

  public ByteBufferByteSequence(ByteBuffer buffer) {
    this.buffer = requireNonNull(buffer, "buffer is null");
  }

  @Override
  public int size() {
    return buffer.remaining();
  }

  @Override
  public byte get(int index) {
    return buffer.get(buffer.position() + index);
  }

  @Override
  public CharBuffer toCharSequence(Charset charset) {
    return charset.decode(buffer.asReadOnlyBuffer()).asReadOnlyBuffer();
  }

  @Override
  public String toString(Charset charset) {
    return charset.decode(buffer.asReadOnlyBuffer()).toString();
  }

  @Override
  public <T> T get(Function<ByteBuffer, T> f) {
    return f.apply(buffer.asReadOnlyBuffer());
  }

  @Override
  public int hashCode() {
    return buffer.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (o instanceof ByteSequence) {
      if (o instanceof ByteBufferByteSequence) {
        return ((ByteBufferByteSequence) o).buffer.equals(buffer);
      }
      var that = (ByteSequence) o;
      var s = size();
      for (int i = 0; i < s; i++) {
        if (get(i) != that.get(i)) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }
  }

  @Override
  public String toString() {
    return toString(StandardCharsets.ISO_8859_1);
  }
}
