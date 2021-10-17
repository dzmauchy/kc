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

import java.io.Serializable;
import java.util.Objects;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

public final class OffsetMetadata implements Comparable<OffsetMetadata>, Serializable {

  public final long offset;
  public final String metadata;

  public OffsetMetadata(long offset, String metadata) {
    this.offset = offset;
    this.metadata = metadata;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (o == null || o.getClass() != OffsetMetadata.class) {
      return false;
    } else {
      var that = (OffsetMetadata) o;
      return this.offset == that.offset && Objects.equals(this.metadata, that.metadata);
    }
  }

  @Override
  public int hashCode() {
    return Long.hashCode(offset) ^ Objects.hashCode(metadata);
  }

  @Override
  public String toString() {
    return metadata == null ? Long.toString(offset) : offset + "@" + metadata;
  }

  @Override
  public int compareTo(OffsetMetadata o) {
    var c = Long.compare(offset, o.offset);
    return c == 0 ? Objects.compare(metadata, o.metadata, nullsFirst(naturalOrder())) : c;
  }
}
