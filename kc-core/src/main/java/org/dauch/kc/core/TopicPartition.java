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

import static java.util.Objects.requireNonNull;

public final class TopicPartition implements Comparable<TopicPartition>, Serializable {

  public final String topic;
  public final int partition;

  public TopicPartition(String topic, int partition) {
    this.topic = requireNonNull(topic, "topic is null");
    this.partition = partition;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (o == null || o.getClass() != TopicPartition.class) {
      return false;
    } else {
      var that = (TopicPartition) o;
      return this.topic.equals(that.topic) && this.partition == that.partition;
    }
  }

  @Override
  public int hashCode() {
    return topic.hashCode() ^ partition;
  }

  @Override
  public String toString() {
    return topic + ":" + partition;
  }

  @Override
  public int compareTo(TopicPartition o) {
    var c = topic.compareTo(o.topic);
    return c == 0 ? Integer.compare(partition, o.partition) : c;
  }
}
