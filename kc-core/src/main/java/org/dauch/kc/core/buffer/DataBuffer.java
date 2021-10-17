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

import java.nio.ByteBuffer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public final class DataBuffer {

  final ByteBuffer buffer;

  public DataBuffer(ByteBuffer buffer) {
    this.buffer = requireNonNull(buffer, "buffer is null");
  }

  public <R> R withBuffer(Function<ByteBuffer, R> f) {
    return f.apply(buffer.asReadOnlyBuffer());
  }

  public int capacity() {
    return buffer.capacity();
  }

  public int size() {
    return buffer.remaining();
  }
}
