package org.dzmauchy.kc.commands;

import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Option;
import groovyjarjarpicocli.CommandLine.Parameters;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.codehaus.groovy.runtime.callsite.BooleanClosureWrapper;
import org.dzmauchy.kc.converters.InstantConverter;
import org.dzmauchy.kc.converters.PropertiesConverter;
import org.dzmauchy.kc.groovy.GroovyShellProvider;
import org.dzmauchy.kc.kafka.Format;
import org.dzmauchy.kc.kafka.KafkaProperties;

import java.rmi.server.UID;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

@Command(
  name = "fetch",
  aliases = {"f"},
  description = "Fetch command",
  mixinStandardHelpOptions = true
)
public class FetchCommand extends AbstractKafkaDataCommand implements Callable<Integer> {

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

  @Option(
    names = {"-f", "--filter"},
    description = "Groovy expression to filter incoming messages",
    defaultValue = "true"
  )
  public String filter;

  @Option(
    names = {"-p", "--projection"},
    description = "Projection expression",
    defaultValue = "$v"
  )
  public String projection;

  @Option(
    names = {"--consumer-properties"},
    description = "Consumer properties",
    converter = PropertiesConverter.class,
    defaultValue = ""
  )
  public KafkaProperties consumerProperties;

  @Option(
    names = {"-t", "--poll-timeout"},
    description = "Poll timeout",
    defaultValue = "PT5S"
  )
  public Duration pollTimeout;

  @Option(
    names = {"-k", "--key-format"},
    description = "Key format",
    defaultValue = "BYTES"
  )
  public Format keyFormat;

  @Option(
    names = {"-v", "--value-format"},
    description = "Value format",
    defaultValue = "BYTES"
  )
  public Format valueFormat;

  @Option(
    names = {"-key-schema"},
    description = "Key schema",
    defaultValue = "Value schema"
  )
  public String keySchema;

  @Parameters(
    description = "Input topics"
  )
  public Set<String> topics;

  @Override
  public Integer call() throws Exception {
    var shell = GroovyShellProvider.defaultShell();
    var filter = groovyFilter(shell);
    var projection = groovyProjection(shell);
    var props = consumerProps();
    try (var consumer = new KafkaConsumer<>(props, new ByteArrayDeserializer(), new ByteArrayDeserializer())) {
      var offs = offsetForTimes(consumer);
      var endOffs = consumer.endOffsets(offs.keySet());
      consumer.assign(offs.keySet());
      offs.forEach((k, v) -> consumer.seek(k, v.offset()));
      offs.keySet().removeIf(tp -> endOffs.getOrDefault(tp, 0L) <= 0L);
      while (!offs.isEmpty()) {
        var pollResult = consumer.poll(pollTimeout);
        pollResult.partitions().parallelStream().forEach(tp -> {
          long endOff = endOffs.getOrDefault(tp, 0L);
          var rawRecords = pollResult.records(tp);

        });
      }
    }
    return 0;
  }

  private BooleanClosureWrapper groovyFilter(GroovyShell shell) {
    return new BooleanClosureWrapper(
      (Closure<?>) shell.evaluate("{$r, $k, $v -> $v.with {CODE}}".replace("CODE", filter))
    );
  }

  private Closure<?> groovyProjection(GroovyShell shell) {
    return (Closure<?>) shell.evaluate("{$r, $k, $v -> $v.with {CODE}}".replace("CODE", projection));
  }

  private TreeMap<String, Object> consumerProps() {
    var props = consumerProperties.getMap();
    props.computeIfAbsent(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, k -> String.join(",", bootstrapServers));
    props.computeIfAbsent(ConsumerConfig.CLIENT_ID_CONFIG, k -> new UID().toString());
    props.computeIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, k -> new UID().toString());
    return props;
  }

  private ConcurrentSkipListMap<TopicPartition, OffsetAndTimestamp> offsetForTimes(KafkaConsumer<?, ?> consumer) {
    var tp = consumer.listTopics().entrySet().stream()
      .filter(e -> topics.contains(e.getKey()))
      .flatMap(e -> e.getValue().stream().map(v -> new TopicPartition(e.getKey(), v.partition())))
      .collect(Collectors.toUnmodifiableMap(e -> e, e -> from.toEpochMilli()));
    final Comparator<TopicPartition> comparator = Comparator
      .comparing(TopicPartition::topic)
      .thenComparingInt(TopicPartition::partition);
    var result = new ConcurrentSkipListMap<TopicPartition, OffsetAndTimestamp>(comparator);
    result.putAll(consumer.offsetsForTimes(tp));
    return result;
  }
}
