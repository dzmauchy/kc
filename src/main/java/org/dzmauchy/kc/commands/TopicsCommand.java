package org.dzmauchy.kc.commands;

import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Option;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.karaf.shell.table.ShellTable;

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

  @Override
  public Integer call() throws Exception {
    try (var client = AdminClient.create(clientProps())) {
      var topics = client.listTopics().names().get().parallelStream()
        .filter(s -> s.matches(pattern))
        .sorted()
        .collect(Collectors.toUnmodifiableList());
      var descriptions = client.describeTopics(topics).all().get();
      var table = new ShellTable();
      table.column("Topic").alignLeft();
      table.column("Partitions").alignCenter();
      table.column("Internal").alignCenter();
      table.column("Operations").alignLeft();
      for (var topic : topics) {
        var description = descriptions.get(topic);
        if (description != null) {
          table.addRow().addContent(
            description.name(),
            description.partitions().size(),
            description.isInternal(),
            description.authorizedOperations().stream().map(AclOperation::code).map(Object::toString).collect(joining(","))
          );
        }
      }
      table.print(System.out);
    }
    return 0;
  }
}
