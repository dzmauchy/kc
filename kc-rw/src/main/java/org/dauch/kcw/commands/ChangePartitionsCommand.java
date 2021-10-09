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
package org.dauch.kcw.commands;

import groovy.json.JsonOutput;
import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Option;
import groovyjarjarpicocli.CommandLine.Parameters;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.config.ConfigResource;
import org.dauch.kcr.commands.AbstractAdminClientCommand;
import org.dauch.kcr.util.ExceptionUtils;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.apache.kafka.clients.admin.NewPartitions.increaseTo;
import static org.apache.kafka.common.config.ConfigResource.Type.TOPIC;

@Command(
  name = "change-partitions",
  aliases = {"P"},
  description = "Change partition count command",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
)
public class ChangePartitionsCommand extends AbstractAdminClientCommand implements Callable<Integer> {

  @Parameters(
    description = "Topics"
  )
  public List<String> topics = emptyList();

  @Option(
    names = {"--retry-on-quota-violation"},
    description = "Retry on quota violation flag",
    fallbackValue = "true",
    defaultValue = "false"
  )
  public boolean retryOnQuotaViolation;

  @Option(
    names = {"--internal"},
    description = "Include internal topics flag",
    fallbackValue = "true",
    defaultValue = "false"
  )
  public boolean internal;

  @Option(
    names = {"-p", "--partitions"},
    description = "Partition count",
    required = true
  )
  public int partitions;

  @Option(
    names = {"--validation-only"},
    description = "Validation only flag",
    fallbackValue = "true",
    defaultValue = "false"
  )
  public boolean validationOnly;

  @Override
  public Integer call() throws Exception {
    if (topics.isEmpty()) {
      err.println("Empty topic list to change partitions");
      return 0;
    }
    try (var client = AdminClient.create(clientProps())) {
      var topicNames = topics(client, internal, topics);
      var describeTopicsOpts = new DescribeTopicsOptions()
        .timeoutMs((int) timeout.toMillis())
        .includeAuthorizedOperations(false);
      var createPartitionsOpts = new CreatePartitionsOptions()
        .timeoutMs((int) timeout.toMillis())
        .validateOnly(validationOnly)
        .retryOnQuotaViolation(retryOnQuotaViolation);
      var deleteTopicOpts = new DeleteTopicsOptions()
        .timeoutMs((int) timeout.toMillis())
        .retryOnQuotaViolation(retryOnQuotaViolation);
      var map = new TreeMap<String, Object>();
      for (var t : topicNames) {
        var prf = client.describeTopics(singleton(t), describeTopicsOpts).all();
        try {
          var pr = prf.get().get(t);
          if (pr != null) {
            var n = pr.partitions().size();
            if (partitions > n) {
              client.createPartitions(Map.of(t, increaseTo(partitions)), createPartitionsOpts).all().get();
              map.put(t, true);
            } else if (partitions < n) {
              var opts = new DescribeConfigsOptions().timeoutMs((int) timeout.toMillis());
              var cr = new ConfigResource(TOPIC, t);
              var dtr = client.describeConfigs(singleton(cr), opts).all().get();
              var r = dtr.get(cr);
              if (r != null) {
                client.deleteTopics(singleton(t), deleteTopicOpts).all().get();
                var cOpts = new CreateTopicsOptions()
                  .retryOnQuotaViolation(retryOnQuotaViolation)
                  .timeoutMs((int) timeout.toMillis())
                  .validateOnly(validationOnly);
                var props = new TreeMap<String, String>();
                r.entries().forEach(e -> props.put(e.name(), e.value()));
                var nt = new NewTopic(t, partitions, (short) pr.partitions().get(0).replicas().size())
                  .configs(props);
                client.createTopics(singleton(nt), cOpts).all().get();
                map.put(t, true);
              } else {
                map.put(t, "NO_CONFIG_INFO");
              }
            }
          } else {
            map.put(t, "NO_DESCRIBE_INFO");
          }
        } catch (Throwable e) {
          map.put(t, ExceptionUtils.exceptionToMap(e));
          if (!quiet) {
            e.printStackTrace(err);
          }
        }
      }
      out.println(finalOutput(JsonOutput.toJson(map)));
    }
    return 0;
  }
}
