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
import groovyjarjarpicocli.CommandLine.Option;
import groovyjarjarpicocli.CommandLine.Parameters;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsOptions;
import org.apache.kafka.clients.admin.ListOffsetsOptions;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.IsolationLevel;
import org.apache.kafka.common.TopicPartition;
import org.apache.karaf.shell.table.ShellTable;
import org.dauch.kcr.converters.TopicPartitionConverter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Callable;

import static groovyjarjarpicocli.CommandLine.Help.Visibility.ALWAYS;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toConcurrentMap;

@Command(
  name = "cgoffsets",
  aliases = {"O"},
  description = "Consumer group offsets",
  mixinStandardHelpOptions = true
)
public class ConsumerGroupOffsetsCommand extends AbstractAdminClientCommand implements Callable<Integer> {

  @Option(
    names = {"-g", "--group"},
    description = "Consumer group"
  )
  public String group;

  @Option(
    names = {"--transactional", "--tx"},
    description = "Transactional enabled",
    defaultValue = "false",
    showDefaultValue = ALWAYS,
    fallbackValue = "true"
  )
  public boolean transactional;

  @Parameters(
    description = "Topic partitions",
    converter = TopicPartitionConverter.class
  )
  public List<TopicPartition> topicPartitions = emptyList();

  @Override
  public Integer call() throws Exception {
    try (var client = AdminClient.create(clientProps())) {
      var hasTopicFilter = topicPartitions.parallelStream().anyMatch(tp -> tp.partition() < 0);
      var options = new ListConsumerGroupOffsetsOptions()
        .timeoutMs((int) timeout.toMillis())
        .topicPartitions(hasTopicFilter ? null : topicPartitions);
      var result = client.listConsumerGroupOffsets(group, options)
        .partitionsToOffsetAndMetadata()
        .get();
      var listOffsetOptions = new ListOffsetsOptions(
        transactional ? IsolationLevel.READ_COMMITTED : IsolationLevel.READ_UNCOMMITTED
      ).timeoutMs((int) timeout.toMillis());
      var earliestRequest = result.keySet().parallelStream()
        .collect(toConcurrentMap(e -> e, e -> OffsetSpec.earliest()));
      var latestRequest = result.keySet().parallelStream()
        .collect(toConcurrentMap(e -> e, e -> OffsetSpec.latest()));
      var earliest = client.listOffsets(earliestRequest, listOffsetOptions).all().get();
      var latest = client.listOffsets(latestRequest, listOffsetOptions).all().get();
      if (!quiet) {
        var table = new ShellTable();
        table.column("Topic").alignLeft();
        table.column("Partition").alignRight();
        table.column("Offset").alignRight();
        table.column("Earliest").alignRight();
        table.column("Latest").alignRight();
        result.forEach((tp, om) -> table.addRow().addContent(
          tp.topic(),
          tp.partition(),
          om.offset(),
          Optional.ofNullable(earliest.get(tp)).map(ListOffsetsResultInfo::offset).orElse(-1L),
          Optional.ofNullable(latest.get(tp)).map(ListOffsetsResultInfo::offset).orElse(-1L)
        ));
        table.print(err);
      }
      var map = new LinkedHashMap<String, TreeMap<Integer, TreeMap<String, Object>>>();
      result.forEach((tp, om) -> {
        var m = map
          .computeIfAbsent(tp.topic(), t -> new TreeMap<>())
          .computeIfAbsent(tp.partition(), p -> new TreeMap<>());
        m.put("offset", om.offset());
        m.put("earliest", Optional.ofNullable(earliest.get(tp)).map(ListOffsetsResultInfo::offset).orElse(-1L));
        m.put("latest", Optional.ofNullable(latest.get(tp)).map(ListOffsetsResultInfo::offset).orElse(-1L));
        om.leaderEpoch().ifPresent(le -> m.put("leaderEpoch", le));
        m.put("metadata", om.metadata());
      });
      out.println(finalOutput(JsonOutput.toJson(map)));
    }
    return 0;
  }
}
