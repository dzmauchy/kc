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
package org.dauch.kcr.commands;

import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Option;
import groovyjarjarpicocli.CommandLine.Parameters;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@Command(
  name = "select",
  aliases = {"s"},
  description = "Select command",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
)
public class SelectCommand extends AbstractFetchCommand implements Callable<Integer> {

  @Parameters(
    description = "topic:partition[:offset[:count]] pairs"
  )
  public List<String> tpos = emptyList();

  @Option(
    names = {"-c"},
    description = "Message count per partition",
    defaultValue = "1"
  )
  public int count;

  private LinkedHashMap<TopicPartition, LongRangeSet> tpos(KafkaConsumer<?, ?> consumer) {
    final var result = new LinkedHashMap<TopicPartition, LongRangeSet>();
    for (var tpo : tpos) {
      var parts = tpo.split(":");
      try {
        var topic = parts[0];
        var partition = Integer.parseInt(parts[1]);
        var tp = new TopicPartition(topic, partition);
        long offset;
        if (parts.length == 2 || parts[2].isEmpty()) {
          var offs = consumer.beginningOffsets(singletonList(tp));
          var off = offs.get(tp);
          if (off == null) {
            throw new IllegalArgumentException("No " + tp + " found");
          } else {
            offset = off;
          }
        } else {
          offset = Long.parseLong(parts[2]);
        }
        int count;
        if (parts.length >= 4) {
          count = Integer.parseInt(parts[3]);
        } else {
          count = this.count;
        }
        result.computeIfAbsent(tp, e -> new LongRangeSet()).union(new LongRange(offset, count));
      } catch (Throwable e) {
        throw new IllegalArgumentException("Invalid pair: " + tpo);
      }
    }
    return result;
  }

  @Override
  public Integer call() throws Exception {
    var state = new FetchState();
    try (var consumer = new KafkaConsumer<>(consumerProps(), BAD, BAD)) {
      var counter = new LongAdder();
      var tpos = tpos(consumer);
      runTasks(ctx -> {
        for (var entry : tpos.entrySet()) {
          var tp = entry.getKey();
          var ranges = entry.getValue();
          var bOffs = consumer.beginningOffsets(singletonList(tp));
          var bOff = bOffs.get(tp);
          if (bOff == null) {
            continue;
          }
          var eOffs = consumer.endOffsets(singletonList(tp));
          var eOff = eOffs.get(tp);
          if (eOff == null || eOff <= bOff) {
            continue;
          }

          RangesLoop:
          for (var range : ranges.ranges) {
            if (range.count <= 0 || range.offset > eOff || !range.intersects(bOff, eOff)) {
              continue;
            }

            consumer.assign(singletonList(tp));
            consumer.seek(tp, range.offset);

            var localCounter = new AtomicInteger();

            MainLoop:
            while (true) {
              var pollResult = consumer.poll(pollTimeout);
              for (var r : pollResult) {
                if (localCounter.incrementAndGet() > range.count) {
                  break MainLoop;
                }
                var k = keyFormat.decode(r.key(), state.keyDecoderProps);
                var v = valueFormat.decode(r.value(), state.valueDecoderProps);
                var params = new Object[]{r, k, v};
                if (!state.filter.call(params)) {
                  continue;
                }
                if (!ctx.filter()) {
                  break RangesLoop;
                }
                var p = state.projection.call(params);
                var res = state.outputFormatter.format(p);
                out.println(res);
                counter.increment();
              }

              var pos = consumer.position(tp);
              if (pos >= eOff) {
                break;
              }
            }
          }
        }
      });
      if (verbose) {
        err.printf("Count: %d%n", counter.sum());
      }
    }
    return 0;
  }

  static final class LongRange {

    final long offset;
    final int count;

    LongRange(long offset, int count) {
      this.offset = offset;
      this.count = count;
    }

    boolean contains(long offset) {
      return offset >= this.offset && offset < this.offset + count;
    }

    boolean contains(LongRange range) {
      return contains(range.offset) && contains(range.offset + range.count - 1);
    }

    boolean intersects(long bOff, long eOff) {
      if (contains(bOff) || contains(eOff)) {
        return true;
      } else {
        var r = new LongRange(bOff, (int) (eOff - bOff) + 1);
        return r.contains(offset) || r.contains(offset + count - 1);
      }
    }

    @Override
    public boolean equals(Object o) {
      return o == this
        || o instanceof LongRange && ((LongRange) o).offset == offset && ((LongRange) o).count == count;
    }

    @Override
    public int hashCode() {
      return Objects.hash(offset, count);
    }

    @Override
    public String toString() {
      return offset + ".." + (offset + count - 1);
    }
  }

  static final class LongRangeSet {

    final HashSet<LongRange> ranges = new HashSet<>();

    void union(LongRange range) {
      if (ranges.isEmpty()) {
        ranges.add(range);
      } else {
        var found = false;
        for (final Iterator<LongRange> it = ranges.iterator(); it.hasNext(); ) {
          var e = it.next();
          if (e.contains(range)) {
            return;
          } else if (range.contains(e)) {
            it.remove();
            union(range);
            found = true;
          } else if (e.contains(range.offset)) {
            it.remove();
            union(new LongRange(e.offset, e.count + (int) (range.offset - range.count - e.offset - e.count)));
            found = true;
          } else if (e.contains(range.offset + range.count - 1)) {
            it.remove();
            union(new LongRange(range.offset, range.count + (int) (e.offset + e.count - range.offset - range.count)));
            found = true;
          }
        }
        if (!found) {
          ranges.add(range);
        }
      }
    }
  }
}
