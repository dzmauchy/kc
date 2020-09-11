package org.dzmauchy.kc.commands;

import groovyjarjarpicocli.CommandLine.Option;

import java.util.List;

import static groovyjarjarpicocli.CommandLine.Help.Visibility.ALWAYS;

public abstract class AbstractKafkaCommand {

  @Option(
    names = {"--bootstrap-servers"},
    paramLabel = "<bootstrap-server>",
    description = "KAFKA bootstrap servers",
    defaultValue = "${env:KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}",
    showDefaultValue = ALWAYS,
    split = ","
  )
  public List<String> bootstrapServers;
}
