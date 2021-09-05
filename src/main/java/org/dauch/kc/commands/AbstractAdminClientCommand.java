package org.dauch.kc.commands;

import groovy.json.JsonOutput;
import groovyjarjarpicocli.CommandLine.Option;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.dauch.kc.kafka.KafkaProperties;
import org.dauch.kc.converters.PropertiesConverter;

import java.rmi.server.UID;
import java.time.Duration;
import java.util.TreeMap;

public abstract class AbstractAdminClientCommand extends AbstractKafkaCommand {

  @Option(
    names = {"--client-properties"},
    description = "Client properties",
    converter = PropertiesConverter.class,
    defaultValue = ""
  )
  public KafkaProperties clientProperties;

  @Option(
    names = {"--timeout"},
    description = "Operation timeout",
    defaultValue = "PT5M"
  )
  public Duration timeout;

  @Option(
    names = {"-p", "--pretty"},
    description = "Pretty print",
    fallbackValue = "true",
    defaultValue = "false"
  )
  public boolean pretty;

  protected TreeMap<String, Object> clientProps() {
    var props = clientProperties.getMap();
    props.computeIfAbsent(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, k -> String.join(",", bootstrapServers));
    props.computeIfAbsent(AdminClientConfig.CLIENT_ID_CONFIG, k -> new UID().toString());
    return props;
  }

  protected String finalOutput(String outputText) {
    return pretty ? JsonOutput.prettyPrint(outputText) : outputText;
  }
}
