package org.ku.kc.commands;

import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Option;
import groovyjarjarpicocli.CommandLine.Parameters;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;

@Command(
  name = "select",
  aliases = {"s"},
  description = "Select command",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
)
public class SelectCommand extends AbstractFetchCommand implements Callable<Integer> {

  @Parameters(
    description = "Input topic:partition:offset pairs"
  )
  public List<String> tpos;

  @Option(
    names = {"-c"},
    description = "Message count per partition",
    defaultValue = "1"
  )
  public long count;

  private IllegalArgumentException formatException(String[] a, Throwable cause) {
    throw new IllegalArgumentException("Invalid topic:partition:offset format: " + String.join(":", a), cause);
  }

  private int partition(String[] a) {
    try {
      return Integer.parseInt(a[1]);
    } catch (Exception e) {
      throw formatException(a, e);
    }
  }

  private long offset(String[] a) {
    try {
      return Long.parseLong(a[2]);
    } catch (Exception e) {
      throw formatException(a, e);
    }
  }

  private List<Map<TopicPartition, Long>> offsets(KafkaConsumer<?, ?> consumer, TopicPartition tp) {
    var bof = (Function<Collection<TopicPartition>, Map<TopicPartition, Long>>) consumer::beginningOffsets;
    var eof = (Function<Collection<TopicPartition>, Map<TopicPartition, Long>>) consumer::endOffsets;
    return Stream.of(bof, eof).map(e -> e.apply(singletonList(tp))).collect(toList());
  }

  private ConcurrentMap<String, ConcurrentLinkedQueue<Entry>> parseTpos(KafkaConsumer<?, ?> consumer) {
    return tpos.parallelStream()
      .map(s -> s.split(":"))
      .collect(Collectors.groupingByConcurrent(
        a -> a[0],
        ConcurrentSkipListMap::new,
        Collectors.flatMapping(
          a -> {
            switch (a.length) {
              case 2: {
                var tp = new TopicPartition(a[0], partition(a));
                var offsets = offsets(consumer, tp);
                var bo = offsets.get(0).get(tp);
                var eo = offsets.get(1).get(tp);
                if (bo != null && eo != null) {
                  if (bo.longValue() == eo.longValue()) {
                    return Stream.empty();
                  } else {
                    return Stream.of(new Entry(tp.partition(), bo, eo));
                  }
                } else {
                  return Stream.empty();
                }
              }
              case 3: {
                var tp = new TopicPartition(a[0], partition(a));
                long offset = offset(a);
                var offsets = offsets(consumer, tp);
                var bo = offsets.get(0).get(tp);
                var eo = offsets.get(1).get(tp);
                if (bo != null && eo != null) {
                  if (offset >= bo && offset <= eo) {
                    if (bo.longValue() == eo.longValue()) {
                      return Stream.empty();
                    } else {
                      return Stream.of(new Entry(tp.partition(), offset, eo));
                    }
                  } else {
                    return Stream.empty();
                  }
                } else {
                  return Stream.empty();
                }
              }
              default: throw formatException(a, new IllegalArgumentException(Integer.toString(a.length)));
            }
          },
          toCollection(ConcurrentLinkedQueue::new)
        )
      ));
  }

  @Override
  public Integer call() throws Exception {
    var state = new FetchState();
    try (var consumer = new KafkaConsumer<>(consumerProps(), BAD, BAD)) {
      var counter = new AtomicInteger();
      var mCounter = new AtomicInteger();
      var tpos = parseTpos(consumer);
      var allTopics = consumer.listTopics();
      tpos.values().removeIf(ConcurrentLinkedQueue::isEmpty);
      tpos.forEach((t, pos) -> {
        var infos = allTopics.get(t);
        if (infos == null) {
          throw new IllegalArgumentException("No such topic: " + t);
        }
        pos.parallelStream().forEach(po -> {
          if (infos.stream().noneMatch(i -> i.partition() == po.partition)) {
            throw new IllegalArgumentException("No partition found for " + t + ": " + po.partition);
          }
        });
      });
      consumer.assign(
        tpos.entrySet().parallelStream()
          .flatMap(e -> e.getValue().stream().map(v -> new TopicPartition(e.getKey(), v.partition)))
          .collect(toConcurrentMap(identity(), t -> true))
          .keySet()
      );
      tpos.forEach((topic, pos) -> {
        var sorted = pos.stream().collect(
          Collectors.groupingBy(
            e -> e.partition,
            Collectors.mapping(e -> e.offset, Collectors.toList())
          )
        );
        sorted.forEach((p, os) -> {
          var minOffset = Collections.min(os);
          consumer.seek(new TopicPartition(topic, p), minOffset);
        });
      });
      runTasks((filter, taskAdder) -> {
        while (!tpos.isEmpty()) {
          reportErrors();
          var pollResult = consumer.poll(pollTimeout);
          taskAdder.addTask(() -> pollResult.partitions().forEach(tp -> {
            var rawRecords = pollResult.records(tp);
            var lastRawRecord = rawRecords.get(rawRecords.size() - 1);
            rawRecords.stream()
              .map(r -> {
                var a = new Object[]{
                  r,
                  keyFormat.decode(r.key(), state.keyDecoderProps),
                  valueFormat.decode(r.value(), state.valueDecoderProps)
                };
                return Map.entry(a, r);
              })
              .filter(e -> state.filter.call(e.getKey()))
              .filter(e -> mCounter.incrementAndGet() <= count)
              .filter(e -> filter.getAsBoolean())
              .map(e -> state.projection.call(e.getKey()))
              .map(state.outputFormatter::format)
              .peek(e -> counter.incrementAndGet())
              .forEachOrdered(out::println);
            tpos.compute(tp.topic(), (t, old) -> {
              if (old == null) {
                return null;
              } else {
                old.removeIf(e -> lastRawRecord.offset() >= e.offset || lastRawRecord.offset() >= e.endOffset);
                return old.isEmpty() ? null : old;
              }
            });
          }));
        }
      });
      if (!quiet) {
        err.printf("Count: %d%n", counter.get());
      }
    }
    return 0;
  }

  private static final class Entry {

    private final int partition;
    private final long offset;
    private final long endOffset;

    private Entry(int partition, long offset, long endOffset) {
      this.partition = partition;
      this.offset = offset;
      this.endOffset = endOffset;
    }

    @Override
    public String toString() {
      return String.format("%d:%d:%d", partition, offset, endOffset);
    }
  }
}
