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

import groovy.json.JsonOutput;
import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Option;
import groovyjarjarpicocli.CommandLine.Parameters;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeConfigsOptions;
import org.apache.kafka.common.config.ConfigResource;
import org.dauch.kcr.util.ExceptionUtils;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(
  name = "describe-config",
  aliases = {"c"},
  description = "Describe config",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
)
public class ConfigCommand extends AbstractAdminClientCommand implements Callable<Integer> {

  @Parameters(
    description = "Config resources"
  )
  public List<String> parameters = Collections.emptyList();

  @Option(
    names = {"-t", "--type"},
    defaultValue = "TOPIC"
  )
  public ConfigResource.Type type = ConfigResource.Type.TOPIC;

  @Option(
    names = {"--include-doc"},
    fallbackValue = "true",
    defaultValue = "false"
  )
  public boolean doc;

  @Option(
    names = {"--include-synonyms"},
    fallbackValue = "true",
    defaultValue = "false"
  )
  public boolean syn;

  @Override
  public Integer call() throws Exception {
    try (var client = AdminClient.create(clientProps())) {
      var opts = new DescribeConfigsOptions()
        .timeoutMs((int) timeout.toMillis())
        .includeDocumentation(doc)
        .includeSynonyms(syn);
      var names = type == ConfigResource.Type.TOPIC
        ? topics(client, false, parameters)
        : parameters;
      var resources = names.parallelStream()
        .map(p -> new ConfigResource(type, p))
        .collect(Collectors.toSet());
      var map = new TreeMap<String, Object>();
      client.describeConfigs(resources, opts).values().forEach((r, f) -> {
        var name = r.name();
        try {
          var v = f.get();
          var m = new TreeMap<String, Object>();
          for (var e : v.entries()) {
            var em = new LinkedHashMap<String, Object>();
            em.put("value", e.value());
            em.put("default", e.isDefault());
            em.put("sensitive", e.isSensitive());
            em.put("type", e.type().name());
            em.put("readOnly", e.isReadOnly());
            em.put("source", e.source().name());
            Optional.ofNullable(e.documentation())
              .filter(s -> !s.isBlank())
              .ifPresent(s -> em.put("doc", s));
            m.put(e.name(), em);
          }
          map.put(name, m);
        } catch (Throwable e) {
          map.put(name, ExceptionUtils.exceptionToMap(e));
          report(e);
        }
      });
      out.println(finalOutput(JsonOutput.toJson(map)));
    }
    return 0;
  }
}
