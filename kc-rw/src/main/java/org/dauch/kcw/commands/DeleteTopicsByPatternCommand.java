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

import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Option;
import groovyjarjarpicocli.CommandLine.Parameters;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DeleteTopicsOptions;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.dauch.kcr.commands.AbstractAdminClientCommand;

import java.util.List;
import java.util.Scanner;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;

@Command(
  name = "delete-topics-by-pattern",
  description = "Delete topics by pattern command",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
)
public class DeleteTopicsByPatternCommand extends AbstractAdminClientCommand implements Callable<Integer> {

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
    names = {"--non-interactive"},
    description = "Non interactive flag",
    fallbackValue = "true",
    defaultValue = "false"
  )
  public boolean nonInteractive;

  @Override
  public Integer call() throws Exception {
    if (topics.isEmpty()) {
      err.println("Empty topic list");
      return 0;
    }
    try (var client = AdminClient.create(clientProps())) {
      var topicNames = client.listTopics(new ListTopicsOptions()
          .listInternal(internal)
          .timeoutMs((int) timeout.toMillis())
        ).names().get().parallelStream()
        .filter(t -> topics.stream().anyMatch(t::matches))
        .collect(Collectors.toCollection(TreeSet::new));
      var deleteOpts = new DeleteTopicsOptions()
        .retryOnQuotaViolation(retryOnQuotaViolation)
        .timeoutMs((int) timeout.toMillis());
      if (nonInteractive) {
        client.deleteTopics(topicNames, deleteOpts).all().get();
      } else {
        var scanner = new Scanner(System.in);
        L:
        for (var t : topicNames) {
          err.printf("Do you really want to delete the topic %s (y/n/c)?%n", t);
          var line = scanner.nextLine().trim().toLowerCase();
          switch (line) {
            case "y":
              client.deleteTopics(singleton(t), deleteOpts).all().get();
              break;
            case "c":
              break L;
          }
        }
      }
    }
    return 0;
  }
}
