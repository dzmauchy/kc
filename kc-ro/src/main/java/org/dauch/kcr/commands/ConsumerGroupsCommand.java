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
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.DescribeConsumerGroupsOptions;
import org.apache.kafka.clients.admin.ListConsumerGroupsOptions;
import org.apache.kafka.common.TopicPartition;
import org.apache.karaf.shell.table.ShellTable;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toConcurrentMap;
import static java.util.stream.Collectors.toList;

@Command(
  name = "groups",
  aliases = {"g"},
  description = "Consumer groups",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
)
public class ConsumerGroupsCommand extends AbstractAdminClientCommand implements Callable<Integer> {

  @Option(
    names = {"--iao", "--include-authorized-operations"},
    description = "Whether include authorized operations or not",
    fallbackValue = "true",
    defaultValue = "false"
  )
  private boolean includeAuthorizedOperations;

  @Parameters(
    description = "Groups"
  )
  public List<String> groups = emptyList();

  private DescribeConsumerGroupsOptions describeOpts() {
    return new DescribeConsumerGroupsOptions()
      .timeoutMs((int) timeout.toMillis())
      .includeAuthorizedOperations(includeAuthorizedOperations);
  }

  private ListConsumerGroupsOptions listOpts() {
    return new ListConsumerGroupsOptions()
      .timeoutMs((int) timeout.toMillis());
  }

  @Override
  public Integer call() throws Exception {
    try (var client = AdminClient.create(clientProps())) {
      var groups = client.listConsumerGroups(listOpts()).all().get().parallelStream()
        .map(ConsumerGroupListing::groupId)
        .filter(s -> this.groups.isEmpty() || this.groups.stream().anyMatch(s::matches))
        .collect(toConcurrentMap(identity(), e -> true, (v1, v2) -> v2, ConcurrentSkipListMap::new))
        .keySet();
      var r = client.describeConsumerGroups(groups, describeOpts()).all().get();
      if (!quiet) {
        var table = new ShellTable();
        table.column("Group").alignLeft();
        table.column("State").alignCenter();
        table.column("Coordinator").alignLeft();
        table.column("Members").alignRight();
        table.column("PAssignor").alignCenter();
        r.forEach((group, description) -> table.addRow().addContent(
          group,
          description.state().toString(),
          description.coordinator(),
          description.members().size(),
          description.partitionAssignor()
        ));
        table.print(err);
      }
      var map = new TreeMap<String, LinkedHashMap<String, Object>>();
      r.forEach((group, description) -> {
        var ops = Stream.ofNullable(description.authorizedOperations())
          .flatMap(Set::stream)
          .sorted(Comparator.comparingInt(e -> Byte.toUnsignedInt(e.code())))
          .map(o -> Map.of("code", Byte.toUnsignedInt(o.code()), "op", o.name()))
          .collect(toList());
        var members = description.members().stream()
          .map(m -> {
            var assignment = m.assignment().topicPartitions().stream()
              .collect(Collectors.toMap(
                TopicPartition::topic,
                TopicPartition::partition,
                (p1, p2) -> p2,
                TreeMap::new
              ));
            var memberMap = new TreeMap<String, Object>();
            memberMap.put("host", m.host());
            memberMap.put("consumerId", m.consumerId());
            memberMap.put("groupInstanceId", m.groupInstanceId());
            memberMap.put("clientId", m.clientId());
            memberMap.put("assignment", assignment);
            return memberMap;
          })
          .collect(toList());
        var coordinator = new LinkedHashMap<String, Object>();
        coordinator.put("id", description.coordinator().idString());
        coordinator.put("host", description.coordinator().host());
        coordinator.put("port", description.coordinator().port());
        coordinator.put("rack", description.coordinator().rack());
        coordinator.put("hostRack", description.coordinator().hasRack());
        var gm = new LinkedHashMap<String, Object>();
        gm.put("partitionAssignor", description.partitionAssignor());
        gm.put("state", description.state().toString());
        gm.put("groupId", description.groupId());
        gm.put("simple", description.isSimpleConsumerGroup());
        gm.put("coordinator", coordinator);
        gm.put("ops", ops);
        gm.put("members", members);
        map.put(group, gm);
      });
      out.println(finalOutput(JsonOutput.toJson(map)));
    }
    return 0;
  }
}
