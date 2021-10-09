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
import org.dauch.kcr.commands.AbstractAdminClientCommand;

import java.util.List;
import java.util.concurrent.Callable;

import static java.util.Collections.emptyList;

@Command(
  name = "delete-topics",
  aliases = {"D"},
  description = "Delete topics command",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
)
public class DeleteTopicsCommand extends AbstractAdminClientCommand implements Callable<Integer> {

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

  @Override
  public Integer call() throws Exception {
    if (topics.isEmpty()) {
      err.println("Empty topic list");
      return 0;
    }
    try (var client = AdminClient.create(clientProps())) {
      var r = client.deleteTopics(
        topics,
        new DeleteTopicsOptions()
          .timeoutMs((int) timeout.toMillis())
          .retryOnQuotaViolation(retryOnQuotaViolation)
      );
      r.all().get();
    }
    return 0;
  }
}
