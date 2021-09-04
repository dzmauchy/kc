package org.ku.kc.commands;

import groovyjarjarpicocli.CommandLine;
import groovyjarjarpicocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(
  description = "Kafka command-line utility",
  footer = {
    "-----------------------------------------------------------",
    "Kafka Command Line on JVM ${java.version} (${java.vendor})"
  },
  mixinStandardHelpOptions = true,
  subcommands = {
    FetchCommand.class,
    SelectCommand.class,
    OffsetsCommand.class,
    TopicsCommand.class,
    SchemaCommand.class,
    ConsumerGroupsCommand.class
  }
)
public class MainCommand implements Callable<Integer> {

  @Override
  public Integer call() {
    CommandLine.usage(this, System.out);
    return 0;
  }
}
