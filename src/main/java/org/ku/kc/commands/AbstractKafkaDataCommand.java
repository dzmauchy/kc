package org.ku.kc.commands;

import groovyjarjarpicocli.CommandLine.Option;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.ku.kc.converters.PropertiesConverter;
import org.ku.kc.kafka.KafkaProperties;

import java.net.URI;
import java.rmi.server.UID;
import java.util.TreeMap;

import static groovyjarjarpicocli.CommandLine.Help.Visibility.ALWAYS;

public abstract class AbstractKafkaDataCommand extends AbstractKafkaCommand {

  protected static final ByteArrayDeserializer BAD = new ByteArrayDeserializer();

  @Option(
    names = {"--schema-registry"},
    paramLabel = "<schema-registry-url>",
    description = "Schema registry URL",
    defaultValue = "${env:SCHEMA_REGISTRY:-${sys:SCHEMA_REGISTRY:-http://localhost:2181/}}",
    showDefaultValue = ALWAYS
  )
  public URI schemaRegistry;

  @Option(
    names = {"--transactional", "--tx"},
    description = "Transactional enabled",
    defaultValue = "false",
    showDefaultValue = ALWAYS,
    fallbackValue = "true"
  )
  public boolean transactional;

  @Option(
    names = {"--group"},
    description = "Kafka consumer group",
    defaultValue = ""
  )
  public String group;

  @Option(
    names = {"--client-properties"},
    description = "Client properties",
    converter = PropertiesConverter.class,
    defaultValue = ""
  )
  public KafkaProperties clientProperties;

  protected TreeMap<String, Object> consumerProps() {
    var props = clientProperties.getMap();
    props.computeIfAbsent(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, k -> String.join(",", bootstrapServers));
    props.computeIfAbsent(ConsumerConfig.CLIENT_ID_CONFIG, k -> new UID().toString());
    if (transactional) {
      props.putIfAbsent(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
    }
    props.putIfAbsent(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "4096");
    props.putIfAbsent(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");
    if (group.isBlank()) {
      props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, new UID().toString());
    } else {
      props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, group);
    }
    return props;
  }
}
