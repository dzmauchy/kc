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

import org.dauch.kc.core.buffer.DataBuffer;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public final class KafkaRecord {

  public final TopicPartition topicPartition;
  public final OffsetMetadata offsetMetadata;
  public final long timestamp;
  public final DataBuffer key;
  public final DataBuffer value;
  public final int leaderEpoch;
  public final Headers headers;

  public KafkaRecord(
    TopicPartition topicPartition,
    OffsetMetadata offsetMetadata,
    long timestamp,
    DataBuffer key,
    DataBuffer value,
    int leaderEpoch,
    Headers headers
  ) {
    this.topicPartition = requireNonNull(topicPartition, "topicPartition is null");
    this.offsetMetadata = requireNonNull(offsetMetadata, "offsetMetadata is null");
    this.timestamp = timestamp;
    this.key = key;
    this.value = value;
    this.leaderEpoch = leaderEpoch;
    this.headers = requireNonNull(headers, "headers is null");
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      topicPartition,
      offsetMetadata,
      timestamp,
      key,
      value,
      leaderEpoch,
      headers
    );
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (o == null || o.getClass() != KafkaRecord.class) {
      return false;
    } else {
      var that = (KafkaRecord) o;
      return Arrays.equals(
        new Object[]{
          this.topicPartition,
          this.offsetMetadata,
          this.timestamp,
          this.key,
          this.value,
          this.leaderEpoch,
          this.headers
        },
        new Object[]{
          that.topicPartition,
          that.offsetMetadata,
          that.timestamp,
          that.key,
          that.value,
          that.leaderEpoch,
          that.headers
        }
      );
    }
  }

  @Override
  public String toString() {
    var map = new LinkedHashMap<String, Object>();
    map.put("tp", topicPartition);
    map.put("om", offsetMetadata);
    map.put("ts", timestamp);
    if (key != null) {
      map.put("ks", key.size());
    }
    if (value != null) {
      map.put("vs", value.size());
    }
    map.put("le", leaderEpoch);
    if (!headers.isEmpty()) {
      map.put("hd", headers);
    }
    return "KafkaRecord" + map;
  }
}