package org.ku.kc.commands;

import groovyjarjarpicocli.CommandLine.Option;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.ku.kc.converters.PropertiesConverter;
import org.ku.kc.kafka.KafkaProperties;

import java.rmi.server.UID;
import java.util.TreeMap;

public abstract class AbstractAdminClientCommand extends AbstractKafkaCommand {

  @Option(
    names = {"--client-properties"},
    description = "Client properties",
    converter = PropertiesConverter.class,
    defaultValue = ""
  )
  public KafkaProperties clientProperties;

  protected TreeMap<String, Object> clientProps() {
    var props = clientProperties.getMap();
    props.computeIfAbsent(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, k -> String.join(",", bootstrapServers));
    props.computeIfAbsent(AdminClientConfig.CLIENT_ID_CONFIG, k -> new UID().toString());
    return props;
  }
}
