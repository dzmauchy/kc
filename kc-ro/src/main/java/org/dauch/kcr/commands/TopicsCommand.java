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
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.karaf.shell.table.ShellTable;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toConcurrentMap;

@Command(
  name = "topics",
  aliases = {"t"},
  description = "Topics command",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
)
public class TopicsCommand extends AbstractAdminClientCommand implements Callable<Integer> {

  @Parameters(
    description = "Topic patterns"
  )
  public List<String> topics = emptyList();

  @Option(
    names = {"--list-internal"},
    description = "Topic name pattern",
    fallbackValue = "true",
    defaultValue = "false"
  )
  public boolean internal;

  @Override
  public Integer call() throws Exception {
    try (var client = AdminClient.create(clientProps())) {
      var opts = new ListTopicsOptions()
        .listInternal(internal);
      var topics = client.listTopics(opts).names().get().parallelStream()
        .filter(s -> this.topics.parallelStream().anyMatch(s::matches))
        .collect(toConcurrentMap(identity(), s -> true, (t1, t2) -> t2, ConcurrentSkipListMap::new));
      var descriptions = client.describeTopics(topics.keySet()).all().get();
      if (!quiet) {
        var table = new ShellTable();
        table.column("Topic").alignLeft();
        table.column("Partitions").alignCenter();
        table.column("Replicas").alignCenter();
        table.column("Operations").alignLeft();
        for (var topic : topics.keySet()) {
          var description = descriptions.get(topic);
          if (description != null) {
            var ops = Stream.ofNullable(description.authorizedOperations())
              .flatMap(Set::stream)
              .map(AclOperation::code)
              .map(Object::toString)
              .sorted()
              .collect(joining(","));
            table.addRow().addContent(
              description.name(),
              description.partitions().size(),
              description.partitions().stream().mapToInt(r -> r.replicas().size()).max().orElse(0),
              ops
            );
          }
        }
        table.print(err);
      }
      var list = new ArrayList<Map<String, Object>>();
      for (var topic : topics.keySet()) {
        var description = descriptions.get(topic);
        if (description != null) {
          var ops = Stream.ofNullable(description.authorizedOperations())
            .flatMap(Set::stream)
            .map(o -> {
              var m = new LinkedHashMap<String, Object>();
              m.put("code", Byte.toUnsignedInt(o.code()));
              m.put("name", o.name());
              return m;
            })
            .collect(Collectors.toList());
          list.add(
            Map.of(
              "topic", description.name(),
              "partitions", description.partitions().size(),
              "replicas", description.partitions().stream().mapToInt(r -> r.replicas().size()).max().orElse(0),
              "operations", ops
            )
          );
        }
      }
      out.println(finalOutput(JsonOutput.toJson(list)));
    }
    return 0;
  }
}
