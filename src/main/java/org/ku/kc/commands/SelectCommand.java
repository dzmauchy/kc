package org.ku.kc.commands;

import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Option;
import groovyjarjarpicocli.CommandLine.Parameters;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.LongAdder;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toConcurrentMap;

@Command(
  name = "select",
  aliases = {"s"},
  description = "Select command",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
)
public class SelectCommand extends AbstractFetchCommand implements Callable<Integer> {

  @Option(
    names = {"-P", "--partition"},
    description = "Partition number",
    defaultValue = "0"
  )
  public int partition;

  @Parameters(
    description = "Input topics"
  )
  public List<String> topics;

  @Option(
    names = {"-F", "--from"},
    description = "Offset to fetch since",
    defaultValue = "today"
  )
  public long from;

  @Option(
    names = {"-T", "--to"},
    description = "Offset to fetch until",
    defaultValue = "now"
  )
  public long to;

  @Override
  public Integer call() throws Exception {
    var state = new FetchState();
    try (var consumer = new KafkaConsumer<>(consumerProps(), BAD, BAD)) {
      var tps = topics.stream()
        .map(t -> new TopicPartition(t, partition))
        .collect(toConcurrentMap(identity(), t -> true));
      consumer.assign(tps.keySet());
      for (var tp : tps.keySet()) {
        consumer.seek(tp, from);
      }
      var counter = new LongAdder();
      while (!tps.isEmpty()) {
        var pollResult = consumer.poll(pollTimeout);
        pollResult.partitions().parallelStream().forEach(tp -> {
          var rawRecords = pollResult.records(tp);
          var lastRawRecord = rawRecords.get(rawRecords.size() - 1);
          if (lastRawRecord.offset() >= to) {
            tps.remove(tp);
          }
          rawRecords.parallelStream()
            .filter(r -> r.offset() >= from && r.offset() < to)
            .map(r -> new Object[]{
              r,
              keyFormat.decode(r.key(), state.keyDecoderProps),
              valueFormat.decode(r.value(), state.valueDecoderProps)
            })
            .filter(state.filter::call)
            .map(state.projection::call)
            .map(state.outputFormatter::format)
            .peek(e -> counter.increment())
            .forEachOrdered(out::println);
        });
      }
      if (!quiet) {
        err.printf("Count: %d%n", counter.sum());
      }
    }
    return 0;
  }
}
