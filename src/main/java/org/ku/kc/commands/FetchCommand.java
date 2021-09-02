package org.ku.kc.commands;

import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Option;
import groovyjarjarpicocli.CommandLine.Parameters;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;
import org.ku.kc.converters.InstantConverter;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

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
  public List<String> topics;

  @Override
  public Integer call() {
    var state = new FetchState();
    try (var consumer = new KafkaConsumer<>(consumerProps(), BAD, BAD)) {
      var offs = offsetForTimes(consumer);
      var endOffs = consumer.endOffsets(offs.keySet());
      offs.entrySet().removeIf(e -> {
        var tp = e.getKey();
        var o = e.getValue();
        return endOffs.getOrDefault(tp, 0L) <= 0L || o.offset() >= endOffs.getOrDefault(tp, 0L);
      });
      if (!quiet) {
        printSubscription(offs, endOffs);
      }
      long fromMillis = from.toEpochMilli();
      long toMillis = to.toEpochMilli();
      consumer.assign(offs.keySet());
      offs.forEach((k, v) -> consumer.seek(k, v.offset()));
      var counter = new LongAdder();
      while (!offs.isEmpty()) {
        var pollResult = consumer.poll(pollTimeout);
        pollResult.partitions().parallelStream().forEach(tp -> {
          long endOff = endOffs.getOrDefault(tp, 1L) - 1L;
          var rawRecords = pollResult.records(tp);
          var lastRawRecord = rawRecords.get(rawRecords.size() - 1);
          if (lastRawRecord.offset() >= endOff || lastRawRecord.timestamp() >= toMillis) {
            offs.remove(tp);
          }
          rawRecords.parallelStream()
            .filter(r -> r.timestamp() >= fromMillis && r.timestamp() < toMillis)
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

  private ConcurrentSkipListMap<TopicPartition, OffsetAndTimestamp> offsetForTimes(KafkaConsumer<?, ?> consumer) {
    var tp = consumer.listTopics().entrySet().stream()
      .filter(e -> topics.parallelStream().anyMatch(p -> e.getKey().matches(p)))
      .flatMap(e -> e.getValue().stream().map(v -> new TopicPartition(e.getKey(), v.partition())))
      .collect(Collectors.toUnmodifiableMap(e -> e, e -> from.toEpochMilli()));
    final Comparator<TopicPartition> comparator = Comparator
      .comparing(TopicPartition::topic)
      .thenComparingInt(TopicPartition::partition);
    var result = new ConcurrentSkipListMap<TopicPartition, OffsetAndTimestamp>(comparator);
    var remoteTimes = consumer.offsetsForTimes(tp);
    remoteTimes.forEach((k, v) -> {
      if (v != null) {
        result.put(k, v);
      }
    });
    return result;
  }
}
