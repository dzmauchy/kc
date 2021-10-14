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
import org.apache.kafka.clients.admin.DescribeTopicsOptions;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsOptions;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.karaf.shell.table.ShellTable;
import org.dauch.kcr.converters.TopicPartitionConverter;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

@Command(
  name = "cgoffsets",
  aliases = {"O"},
  description = "Consumer group offsets",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
)
public class ConsumerGroupOffsetsCommand extends AbstractAdminClientCommand implements Callable<Integer> {

  @Option(
    names = {"-g", "--group"},
    description = "Consumer group"
  )
  public String group = "";

  @Option(
    names = {"--transactional", "--tx"},
    description = "Transactional enabled",
    defaultValue = "false",
    fallbackValue = "true"
  )
  public boolean transactional;

  @Option(
    names = {"--fetch-timeout", "-T"},
    description = "Fetch timeout",
    defaultValue = "PT10S"
  )
  public Duration fetchTimeout = Duration.ofSeconds(10L);

  @Parameters(
    description = "Topic partitions",
    converter = TopicPartitionConverter.class
  )
  public List<TopicPartition> topicPartitions;

  private ListConsumerGroupOffsetsOptions opts(AdminClient client) throws Exception {
    final List<TopicPartition> tps;
    if (topicPartitions != null && topicPartitions.parallelStream().anyMatch(tp -> tp.partition() < 0)) {
      var topicsToDescribe = topicPartitions.parallelStream()
        .filter(tp -> tp.partition() < 0)
        .map(TopicPartition::topic)
        .collect(Collectors.toSet());
      var topics = client.describeTopics(topicsToDescribe, new DescribeTopicsOptions()
        .timeoutMs((int) timeout.toMillis())
        .includeAuthorizedOperations(false)
      ).all().get();
      tps = topicPartitions.parallelStream()
        .flatMap(tp -> {
          if (tp.partition() < 0) {
            return Optional.ofNullable(topics.get(tp.topic()))
              .map(d -> d.partitions().stream().map(e -> new TopicPartition(tp.topic(), e.partition())))
              .orElse(Stream.empty());
          } else {
            return Stream.of(tp);
          }
        })
        .collect(toList());
    } else {
      tps = topicPartitions;
    }
    return new ListConsumerGroupOffsetsOptions()
      .timeoutMs((int) timeout.toMillis())
      .topicPartitions(tps);
  }

  @Override
  public Integer call() throws Exception {
    try (var client = AdminClient.create(clientProps())) {
      var result = tpMap(client.listConsumerGroupOffsets(group, opts(client))
        .partitionsToOffsetAndMetadata()
        .get());
      var consumerProps = clientProps();
      consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
      consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_DOC, "earliest");
      consumerProps.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, "1");
      if (transactional) {
        consumerProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
      }
      var map = new TreeMap<String, TreeMap<Integer, TreeMap<String, Object>>>();
      try (var consumer = new KafkaConsumer<>(consumerProps, new ByteArrayDeserializer(), new ByteArrayDeserializer())) {
        var endOffsets = consumer.endOffsets(result.keySet(), timeout);
        var beginOffsets = consumer.beginningOffsets(result.keySet(), timeout);
        var lags = new HashMap<TopicPartition, Long>();
        consumer.assign(result.keySet());
        result.forEach((tp, om) -> {
          if (om != null) {
            consumer.seek(tp, om);
          }
        });
        var batch = consumer.poll(fetchTimeout);
        var groupedBatch = batch.partitions().parallelStream()
          .flatMap(tp -> batch.records(tp).stream())
          .collect(Collectors.groupingByConcurrent(
            e -> new TopicPartition(e.topic(), e.partition()),
            Collectors.counting()
          ));
        result.forEach((tp, om) -> {
          final long lag;
          if (om == null) {
            lag = -1L;
          } else {
            final long eo = endOffsets.getOrDefault(tp, -1L);
            if (eo >= 0L) {
              var op = consumer.position(tp, timeout);
              lag = Math.max(0L, eo - op + groupedBatch.getOrDefault(tp, 0L));
            } else {
              lag = -1L;
            }
          }
          lags.put(tp, lag);
        });
        if (!quiet) {
          var table = new ShellTable();
          table.column("Topic").alignLeft();
          table.column("Partition").alignRight();
          table.column("Earliest").alignRight();
          table.column("Offset").alignRight();
          table.column("Latest").alignRight();
          table.column("Lag").alignRight();
          table.column("ActualLag").alignRight();
          result.forEach((tp, om) -> {
            var bo = beginOffsets.getOrDefault(tp, -1L);
            var o = om == null ? -1L : om.offset();
            var eo = endOffsets.getOrDefault(tp, -1L);
            var lag = (eo < 0L || o < 0L) ? -1L : eo - o;
            var alag = lags.getOrDefault(tp, -1L);
            table.addRow().addContent(tp.topic(), tp.partition(), bo, o, eo, lag, alag);
          });
          table.print(err);
        }
        result.forEach((tp, om) -> {
          var m = map
            .computeIfAbsent(tp.topic(), t -> new TreeMap<>())
            .computeIfAbsent(tp.partition(), p -> new TreeMap<>());
          m.put("offset", om == null ? -1L : om.offset());
          m.put("earliest", beginOffsets.getOrDefault(tp, -1L));
          m.put("latest", endOffsets.getOrDefault(tp, -1L));
          m.put("actualLag", lags.getOrDefault(tp, -1L));
          ofNullable(om)
            .flatMap(OffsetAndMetadata::leaderEpoch)
            .ifPresent(le -> m.put("leaderEpoch", le));
          ofNullable(om)
            .ifPresent(d -> m.put("metadata", d.metadata()));
        });
      }
      out.println(finalOutput(JsonOutput.toJson(map)));
    }
    return 0;
  }
}
