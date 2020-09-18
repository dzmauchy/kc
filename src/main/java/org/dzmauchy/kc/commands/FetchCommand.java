package org.dzmauchy.kc.commands;

import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Option;
import groovyjarjarpicocli.CommandLine.Parameters;
import org.apache.avro.Schema;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.karaf.shell.table.ShellTable;
import org.codehaus.groovy.runtime.callsite.BooleanClosureWrapper;
import org.dzmauchy.kc.converters.InstantConverter;
import org.dzmauchy.kc.converters.PropertiesConverter;
import org.dzmauchy.kc.format.OutputFormatter;
import org.dzmauchy.kc.groovy.GroovyShellProvider;
import org.dzmauchy.kc.kafka.DecoderKey;
import org.dzmauchy.kc.kafka.Format;
import org.dzmauchy.kc.kafka.KafkaProperties;

import java.rmi.server.UID;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

@Command(
  name = "fetch",
  aliases = {"f"},
  description = "Fetch command",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
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
    defaultValue = "[k: $k, v: $v]"
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
    defaultValue = "${env:KC_KEY_FORMAT:-BYTES}"
  )
  public Format keyFormat;

  @Option(
    names = {"-v", "--value-format"},
    description = "Value format",
    defaultValue = "${env:KC_VALUE_FORMAT:-BYTES}"
  )
  public Format valueFormat;

  @Option(
    names = {"--key-schema"},
    description = "Key schema",
    defaultValue = "${env:KC_KEY_SCHEMA:-SCHEMA_REGISTRY}"
  )
  public String keySchema;

  @Option(
    names = {"--value-schema"},
    description = "Value schema",
    defaultValue = "${env:KC_VALUE_SCHEMA:-SCHEMA_REGISTRY}"
  )
  public String valueSchema;

  @Parameters(
    description = "Input topics"
  )
  public List<String> topics;

  @Override
  public Integer call() throws Exception {
    var outputFormatter = new OutputFormatter();
    var shell = GroovyShellProvider.defaultShell();
    var filter = groovyFilter(shell);
    var projection = groovyProjection(shell);
    var props = consumerProps();
    var keySchema = parseSchema(this.keySchema);
    var valueSchema = parseSchema(this.valueSchema);
    var keyDecoderProps = decoderProps(keySchema);
    var valueDecoderProps = decoderProps(valueSchema);
    try (var consumer = new KafkaConsumer<>(props, new ByteArrayDeserializer(), new ByteArrayDeserializer())) {
      var offs = offsetForTimes(consumer);
      var endOffs = consumer.endOffsets(offs.keySet());
      offs.keySet().removeIf(tp -> endOffs.getOrDefault(tp, 0L) <= 0L);
      if (!quiet) {
        printSubscription(offs, endOffs);
      }
      long fromMillis = from.toEpochMilli();
      long toMillis = to.toEpochMilli();
      consumer.assign(offs.keySet());
      offs.forEach((k, v) -> consumer.seek(k, v.offset()));
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
              keyFormat.decode(r.key(), keyDecoderProps),
              valueFormat.decode(r.value(), valueDecoderProps)
            })
            .filter(filter::call)
            .map(projection::call)
            .map(outputFormatter::format)
            .forEachOrdered(out::println);
        });
      }
    }
    return 0;
  }

  private void printSubscription(Map<TopicPartition, OffsetAndTimestamp> offs, Map<TopicPartition, Long> endOffs) {
    var table = new ShellTable();
    table.column("Topic").alignLeft();
    table.column("Partition").alignRight();
    table.column("Offset").alignRight();
    table.column("Timestamp").alignCenter();
    table.column("End offset").alignRight();
    offs.forEach((tp, omd) -> {
      table.addRow().addContent(
        tp.topic(),
        tp.partition(),
        omd.offset(),
        Instant.ofEpochMilli(omd.timestamp()),
        endOffs.getOrDefault(tp, -1L)
      );
    });
    table.print(err);
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
      } else {
        logger.warn("No data for {}", k);
      }
    });
    return result;
  }

  private Schema parseSchema(String schema) {
    switch (schema) {
      case "SCHEMA_REGISTRY": return null;
      case "STRING": return Schema.create(Schema.Type.STRING);
      case "LONG": return Schema.create(Schema.Type.LONG);
      case "INT": return Schema.create(Schema.Type.INT);
      case "DOUBLE": return Schema.create(Schema.Type.DOUBLE);
      case "FLOAT": return Schema.create(Schema.Type.FLOAT);
      case "BYTES": return Schema.create(Schema.Type.BYTES);
      case "BOOLEAN": return Schema.create(Schema.Type.BOOLEAN);
      default: return new Schema.Parser().parse(schema);
    }
  }

  private EnumMap<DecoderKey, Object> decoderProps(Schema schema) {
    var map = new EnumMap<>(DecoderKey.class);
    map.put(DecoderKey.SCHEMA_REGISTRY, schemaRegistry);
    map.put(DecoderKey.SCHEMA, schema);
    return map;
  }
}
