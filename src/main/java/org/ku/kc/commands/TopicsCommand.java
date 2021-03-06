package org.ku.kc.commands;

import groovy.json.JsonOutput;
import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Option;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.karaf.shell.table.ShellTable;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

@Command(
  name = "topics",
  aliases = {"t"},
  description = "Topics command",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
)
public class TopicsCommand extends AbstractAdminClientCommand implements Callable<Integer> {

  @Option(
    names = {"-p", "--pattern"},
    description = "Topic name pattern",
    defaultValue = ".+"
  )
  public String pattern;

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
        .filter(s -> s.matches(pattern))
        .sorted()
        .collect(Collectors.toUnmodifiableList());
      var descriptions = client.describeTopics(topics).all().get();
      if (!quiet) {
        var table = new ShellTable();
        table.column("Topic").alignLeft();
        table.column("Partitions").alignCenter();
        table.column("Replicas").alignCenter();
        table.column("Operations").alignLeft();
        for (var topic : topics) {
          var description = descriptions.get(topic);
          if (description != null) {
            table.addRow().addContent(
              description.name(),
              description.partitions().size(),
              description.partitions().stream().mapToInt(r -> r.replicas().size()).max().orElse(0),
              description.authorizedOperations().stream().map(AclOperation::code).map(Object::toString).collect(joining(","))
            );
          }
        }
        table.print(err);
      }
      var list = new ArrayList<Map<String, Object>>();
      for (var topic : topics) {
        var description = descriptions.get(topic);
        if (description != null) {
          list.add(
            Map.of(
              "topic", description.name(),
              "partitions", description.partitions().size(),
              "replicas", description.partitions().stream().mapToInt(r -> r.replicas().size()).max().orElse(0),
              "operations", description.authorizedOperations().stream().mapToInt(AclOperation::code).toArray()
            )
          );
        }
      }
      out.println(JsonOutput.toJson(list));
    }
    return 0;
  }
}
