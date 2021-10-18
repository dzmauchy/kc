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

import groovy.json.JsonOutput;
import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Parameters;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.karaf.shell.table.ShellTable;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListMap;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;

@Command(
  name = "offsets",
  aliases = {"o"},
  description = "Offset-related command",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
)
public class OffsetsCommand extends AbstractAdminClientCommand implements Callable<Integer> {

  @Parameters(
    description = "Input topics"
  )
  public List<String> topics = emptyList();

  @Override
  public Integer call() throws Exception {
    var offsetFormat = new DecimalFormat("0.0000E00");
    try (var consumer = new KafkaConsumer<>(clientProps(), new ByteArrayDeserializer(), new ByteArrayDeserializer())) {
      var partitions = consumer.listTopics(timeout).entrySet().parallelStream()
        .filter(e -> topics.stream().anyMatch(p -> e.getKey().matches(p)))
        .collect(
          toConcurrentMap(
            Map.Entry::getKey,
            e -> e.getValue().stream()
              .map(p -> new TopicPartition(e.getKey(), p.partition()))
              .sorted(this::compareTps)
              .collect(toList()),
            (o1, o2) -> o2,
            ConcurrentSkipListMap::new
          )
        );
      var result = new LinkedHashMap<String, LinkedHashMap<Integer, LinkedHashMap<String, Object>>>();
      for (var e : partitions.entrySet()) {
        var topic = e.getKey();
        var topicPartitions = e.getValue();
        var endOffsets = consumer.endOffsets(topicPartitions);
        var beginOffsets = consumer.beginningOffsets(topicPartitions);
        var startTimestamps = consumer.offsetsForTimes(topicPartitions.stream().collect(toMap(identity(), tp -> 0L)));
        startTimestamps.values().removeIf(Objects::isNull);
        var pMap = result.computeIfAbsent(topic, k -> new LinkedHashMap<>());
        for (var tp : topicPartitions) {
          var iMap = pMap.computeIfAbsent(tp.partition(), k -> new LinkedHashMap<>());
          var endOff = endOffsets.getOrDefault(tp, -1L);
          var startOff = beginOffsets.getOrDefault(tp, -1L);
          var startTimestamp = startTimestamps.getOrDefault(tp, new OffsetAndTimestamp(0L, 0L));
          iMap.put("startOffset", startOff);
          iMap.put("endOffset", endOff);
          iMap.put("startTime", Instant.ofEpochMilli(startTimestamp.timestamp()).toString());
        }
        if (verbose) {
          err.println(topic);
          var table = new ShellTable();
          table.column("Partition").alignLeft();
          table.column("Start offset").alignRight();
          table.column("End offset").alignRight();
          table.column("Offset diff").alignRight();
          table.column("Start time").alignLeft();
          for (var tp : topicPartitions) {
            var endOff = endOffsets.getOrDefault(tp, -1L);
            var startOff = beginOffsets.getOrDefault(tp, -1L);
            var startTimestamp = startTimestamps.getOrDefault(tp, new OffsetAndTimestamp(0L, 0L));
            table.addRow().addContent(
              tp.partition(),
              startOff,
              endOff,
              offsetFormat.format(endOff - startOff),
              defaultTimeFormatter.format(Instant.ofEpochMilli(startTimestamp.timestamp()).atOffset(ZoneOffset.UTC))
            );
          }
          table.print(err);
        }
      }
      out.println(finalOutput(JsonOutput.toJson(result)));
    }
    return 0;
  }
}
