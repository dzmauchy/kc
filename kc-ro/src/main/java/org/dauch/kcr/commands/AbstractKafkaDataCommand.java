/*
 * Copyright 2021 Dzmiter Auchynnikau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dauch.kcr.commands;

import groovyjarjarpicocli.CommandLine.Option;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.dauch.kcr.kafka.KafkaProperties;
import org.dauch.kcr.converters.PropertiesConverter;

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
