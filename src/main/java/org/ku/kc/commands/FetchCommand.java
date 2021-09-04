package org.ku.kc.commands;

import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Option;
import groovyjarjarpicocli.CommandLine.Parameters;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;
import org.ku.kc.converters.InstantConverter;

import java.time.Instant;
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
  public Integer call() throws Exception {
    var state = new FetchState();
    try (var consumer = new KafkaConsumer<>(consumerProps(), BAD, BAD)) {
      var offs = offsetForTimes(consumer);
      var beginOffs = tpMap(consumer.beginningOffsets(offs.keySet()));
      var endOffs = tpMap(consumer.endOffsets(offs.keySet()));
      offs.entrySet().removeIf(e -> {
        var tp = e.getKey();
        var o = e.getValue();
        long eo = endOffs.getOrDefault(tp, 0L);
        return eo <= beginOffs.getOrDefault(tp, 0L) || o.offset() >= endOffs.getOrDefault(tp, 0L);
      });
      if (!quiet) {
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
          ctx.addTask(() -> pollResult.partitions().parallelStream().forEach(tp -> {
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
            long endOff = endOffs.getOrDefault(tp, 1L);
            var lr = rawRecords.get(rawRecords.size() - 1);
            if (lr.offset() >= endOff || lr.timestamp() >= toMillis) {
              offs.remove(tp);
            } else {
              final long pos;
              synchronized (consumer) {
                pos = consumer.position(tp);
              }
              if (pos >= endOff) {
                offs.remove(tp);
              }
            }
          }));
        }
      });
      if (!quiet) {
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
