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
import org.apache.kafka.clients.admin.DescribeTopicsOptions;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.karaf.shell.table.ShellTable;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;

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
    description = "Whether include internal topics too",
    fallbackValue = "true",
    defaultValue = "false"
  )
  public boolean internal;

  @Option(
    names = {"--opts"},
    description = "Include authorized operations",
    fallbackValue = "true",
    defaultValue = "false"
  )
  public boolean ops;

  private LinkedHashMap<String, Object> node(Node node) {
    var map = new LinkedHashMap<String, Object>(4);
    map.put("name", node.idString());
    map.put("id", node.id());
    map.put("host", node.host());
    map.put("port", node.port());
    if (node.hasRack()) {
      map.put("rack", node.rack());
    }
    map.put("empty", node.isEmpty());
    return map;
  }

  @Override
  public Integer call() throws Exception {
    try (var client = AdminClient.create(clientProps())) {
      var opts = new ListTopicsOptions()
        .listInternal(internal)
        .timeoutMs((int) timeout.toMillis());
      var dOpts = new DescribeTopicsOptions()
        .timeoutMs((int) timeout.toMillis())
        .includeAuthorizedOperations(ops);
      var topics = client.listTopics(opts).names().get().parallelStream()
        .filter(s -> this.topics.isEmpty() || this.topics.parallelStream().anyMatch(s::matches))
        .collect(toConcurrentMap(identity(), s -> true, (t1, t2) -> t2, ConcurrentSkipListMap::new));
      var descriptions = client.describeTopics(topics.keySet(), dOpts).all().get();
      if (verbose) {
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
          var map = new LinkedHashMap<String, Object>(4);
          map.put("topic", description.name());
          map.put("partitions", description.partitions().stream()
            .map(p -> {
              var partitionMap = new LinkedHashMap<String, Object>();
              partitionMap.put("id", p.partition());
              if (p.leader() != null) {
                partitionMap.put("leader", node(p.leader()));
              }
              partitionMap.put("replicas", p.replicas().stream().map(this::node).collect(toList()));
              partitionMap.put("isr", p.isr().stream().map(this::node).collect(toList()));
              return partitionMap;
            })
            .collect(toList())
          );
          if (description.authorizedOperations() != null) {
            map.put("operations", description.authorizedOperations().stream()
              .map(o -> {
                var m = new LinkedHashMap<String, Object>();
                m.put("code", Byte.toUnsignedInt(o.code()));
                m.put("name", o.name());
                return m;
              })
              .collect(toList())
            );
          }
          list.add(map);
        }
      }
      out.println(finalOutput(JsonOutput.toJson(list)));
    }
    return 0;
  }
}
