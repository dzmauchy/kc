package org.ku.kc.commands;

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

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;

@Command(
  name = "offsets",
  aliases = {"o"},
  description = "Offset-related command",
  mixinStandardHelpOptions = true
)
public class OffsetsCommand extends AbstractAdminClientCommand implements Callable<Integer> {

  @Parameters(
    description = "Input topics"
  )
  public List<String> topics;

  @Override
  public Integer call() throws Exception {
    var offsetFormat = new DecimalFormat("0.0000E00");
    try (var consumer = new KafkaConsumer<>(clientProps(), new ByteArrayDeserializer(), new ByteArrayDeserializer())) {
      var partitions = consumer.listTopics().entrySet().parallelStream()
        .filter(e -> topics.stream().anyMatch(p -> e.getKey().matches(p)))
        .collect(
          toConcurrentMap(
            Map.Entry::getKey,
            e -> e.getValue().stream().map(p -> new TopicPartition(e.getKey(), p.partition())).sorted(tpc()).collect(toList()),
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
        if (!quiet) {
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
      out.println(JsonOutput.toJson(result));
    }
    return 0;
  }
}
