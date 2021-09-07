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
