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
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;
import org.dauch.kcr.converters.InstantConverter;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

@Command(
  name = "fetch",
  aliases = {"f"},
  description = "Fetch command",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
)
public class FetchCommand extends AbstractFetchCommand implements Callable<Integer> {

  @Option(
    names = {"-F", "--from"},
    description = "Date to fetch since",
    converter = InstantConverter.class,
    defaultValue = "today"
  )
  public Instant from;

  @Option(
    names = {"-T", "--to"},
    description = "Date to fetch until",
    converter = InstantConverter.class,
    defaultValue = "now"
  )
  public Instant to;

  @Parameters(
    description = "Input topics"
  )
  public List<String> topics = emptyList();

  @Override
  public Integer call() throws Exception {
    var state = new FetchState();
    try (var consumer = new KafkaConsumer<>(consumerProps(), BAD, BAD)) {
      var offs = offsetForTimes(consumer);
      var beginOffs = tpMap(consumer.beginningOffsets(offs.keySet()));
      var endOffs = tpMap(consumer.endOffsets(offs.keySet()));
      if (!wait) {
        offs.entrySet().removeIf(e -> {
          var tp = e.getKey();
          var o = e.getValue();
          long eo = endOffs.getOrDefault(tp, 0L);
          return eo <= beginOffs.getOrDefault(tp, 0L) || o.offset() >= endOffs.getOrDefault(tp, 0L);
        });
      }
      if (verbose) {
        printSubscription(offs, endOffs);
      }
      long fromMillis = from.toEpochMilli();
      long toMillis = to.toEpochMilli();
      consumer.assign(offs.keySet());
      offs.forEach((k, v) -> consumer.seek(k, v.offset()));
      var counter = new LongAdder();
      runTasks(ctx -> {
        while (!offs.isEmpty()) {
          final ConsumerRecords<byte[], byte[]> pollResult;
          synchronized (consumer) {
            pollResult = consumer.poll(pollTimeout);
          }
          ctx.addTask(() -> {
            if (pollResult.isEmpty()) {
              if (!wait) {
                offs.keySet().removeIf(tp -> {
                  final long pos;
                  synchronized (consumer) {
                    pos = consumer.position(tp);
                  }
                  return pos >= endOffs.getOrDefault(tp, 1L);
                });
              }
            } else {
              pollResult.partitions().parallelStream().forEach(tp -> {
                var rawRecords = pollResult.records(tp);
                rawRecords.parallelStream()
                  .filter(r -> r.timestamp() >= fromMillis && r.timestamp() < toMillis)
                  .map(r -> new Object[]{
                    r,
                    keyFormat.decode(r.key(), state.keyDecoderProps),
                    valueFormat.decode(r.value(), state.valueDecoderProps)
                  })
                  .filter(state.filter::call)
                  .filter(ctx::filter)
                  .map(state.projection::call)
                  .map(state.outputFormatter::format)
                  .peek(e -> counter.increment())
                  .forEachOrdered(out::println);
                var lr = rawRecords.get(rawRecords.size() - 1);
                if (lr.timestamp() >= toMillis || !wait && lr.offset() >= endOffs.getOrDefault(tp, 1L)) {
                  offs.remove(tp);
                }
              });
            }
          });
        }
      });
      if (verbose) {
        err.printf("Count: %d%n", counter.sum());
      }
    }
    return 0;
  }

  private ConcurrentSkipListMap<TopicPartition, OffsetAndTimestamp> offsetForTimes(KafkaConsumer<?, ?> consumer) {
    var tpFrom = consumer.listTopics().entrySet().stream()
      .filter(e -> topics.parallelStream().anyMatch(p -> e.getKey().matches(p)))
      .flatMap(e -> e.getValue().stream().map(v -> new TopicPartition(e.getKey(), v.partition())))
      .collect(Collectors.toUnmodifiableMap(e -> e, e -> from.toEpochMilli()));
    var result = new ConcurrentSkipListMap<TopicPartition, OffsetAndTimestamp>(this::compareTps);
    var remoteTimes = consumer.offsetsForTimes(tpFrom);
    remoteTimes.forEach((k, v) -> {
      if (v != null) {
        result.put(k, v);
      }
    });
    return result;
  }
}
