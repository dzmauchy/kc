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

import groovyjarjarpicocli.CommandLine;
import groovyjarjarpicocli.CommandLine.Command;
import org.dauch.kcr.commands.*;

import java.util.concurrent.Callable;

@Command(
  description = "Kafka command-line read-write utility",
  footer = {
    "-----------------------------------------------------------",
    "Kafka Command Line on JVM ${java.version} (${java.vendor})"
  },
  mixinStandardHelpOptions = true,
  subcommands = {
    // read only
    FetchCommand.class,
    SelectCommand.class,
    OffsetsCommand.class,
    TopicsCommand.class,
    SchemaCommand.class,
    ConsumerGroupsCommand.class,
    ConsumerGroupOffsetsCommand.class,
    // read-write
    DeleteTopicsCommand.class,
    ChangePartitionsCommand.class
  }
)
public class KcwCommand implements Callable<Integer> {

  @Override
  public Integer call() {
    CommandLine.usage(this, System.out);
    return 0;
  }
}
