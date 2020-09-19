package org.ku.kc.commands;

import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import groovyjarjarpicocli.CommandLine.Option;
import org.apache.avro.Schema;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;
import org.apache.karaf.shell.table.ShellTable;
import org.codehaus.groovy.runtime.callsite.BooleanClosureWrapper;
import org.ku.kc.converters.PropertiesConverter;
import org.ku.kc.format.OutputFormatter;
import org.ku.kc.groovy.GroovyShellProvider;
import org.ku.kc.kafka.DecoderKey;
import org.ku.kc.kafka.Format;
import org.ku.kc.kafka.KafkaProperties;

import java.rmi.server.UID;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

public abstract class AbstractFetchCommand extends AbstractKafkaDataCommand {

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

  protected void printSubscription(Map<TopicPartition, OffsetAndTimestamp> offs, Map<TopicPartition, Long> endOffs) {
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

  protected BooleanClosureWrapper groovyFilter(GroovyShell shell) {
    return new BooleanClosureWrapper(
      (Closure<?>) shell.evaluate("{$r, $k, $v -> $v.with {CODE}}".replace("CODE", filter))
    );
  }

  protected Closure<?> groovyProjection(GroovyShell shell) {
    return (Closure<?>) shell.evaluate("{$r, $k, $v -> $v.with {CODE}}".replace("CODE", projection));
  }

  protected TreeMap<String, Object> consumerProps() {
    var props = consumerProperties.getMap();
    props.computeIfAbsent(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, k -> String.join(",", bootstrapServers));
    props.computeIfAbsent(ConsumerConfig.CLIENT_ID_CONFIG, k -> new UID().toString());
    return props;
  }

  protected Schema parseSchema(String schema) {
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

  protected EnumMap<DecoderKey, Object> decoderProps(Schema schema) {
    var map = new EnumMap<>(DecoderKey.class);
    map.put(DecoderKey.SCHEMA_REGISTRY, schemaRegistry);
    map.put(DecoderKey.SCHEMA, schema);
    return map;
  }

  protected class FetchState {

    public final OutputFormatter outputFormatter = new OutputFormatter();
    public final GroovyShell shell = GroovyShellProvider.defaultShell();
    public final BooleanClosureWrapper filter = groovyFilter(shell);
    public final Closure<?> projection = groovyProjection(shell);
    public final Schema keySchema = parseSchema(AbstractFetchCommand.this.keySchema);
    public final Schema valueSchema = parseSchema(AbstractFetchCommand.this.valueSchema);
    public final EnumMap<DecoderKey, Object> keyDecoderProps = decoderProps(keySchema);
    public final EnumMap<DecoderKey, Object> valueDecoderProps = decoderProps(valueSchema);
  }
}
