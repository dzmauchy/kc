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
package org.dauch.kc.core.client;

import org.dauch.kc.core.ByteSequence;
import org.dauch.kc.core.Headers;

import java.util.Collections;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

final class HeadersImpl implements Headers {

  private final TreeMap<String, ByteSequence> map = new TreeMap<>();

  void add(String key, ByteSequence value) {
    map.put(Objects.requireNonNull(key, "key is null"), value);
  }

  @Override
  public ByteSequence get(String key) {
    return map.get(key);
  }

  @Override
  public boolean contains(String key) {
    return map.containsKey(key);
  }

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public boolean isEmpty() {
    return map.isEmpty();
  }

  @Override
  public Iterable<String> keys() {
    //noinspection FunctionalExpressionCanBeFolded
    return map.keySet()::iterator;
  }

  @Override
  public Stream<String> keyStream() {
    return map.keySet().stream();
  }

  @Override
  public Iterable<Entry<String, ByteSequence>> entries() {
    //noinspection FunctionalExpressionCanBeFolded
    return map.entrySet()::iterator;
  }

  @Override
  public Stream<Entry<String, ByteSequence>> entryStream() {
    return map.entrySet().stream();
  }

  @Override
  public NavigableMap<String, ByteSequence> asMap() {
    return Collections.unmodifiableNavigableMap(map);
  }

  @Override
  public int hashCode() {
    return map.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (o instanceof Headers) {
      if (o.getClass() == HeadersImpl.class) {
        return ((HeadersImpl) o).map.equals(map);
      }
      var that = (Headers) o;
      if (this.size() == that.size()) {
        if (this.keyStream().allMatch(that::contains)) {
          return keyStream().allMatch(k -> Objects.equals(get(k), that.get(k)));
        } else {
          return false;
        }
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

  @Override
  public String toString() {
    return "Headers{" + map + "}";
  }
}
