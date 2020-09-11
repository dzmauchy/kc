package org.dzmauchy.kc.commands;

import groovyjarjarpicocli.CommandLine;
import groovyjarjarpicocli.CommandLine.Option;

import java.net.URL;

public abstract class AbstractKafkaCommand {

  @Option(
    names = {"--schema-registry"},
    description = "Schema registry URL",
    defaultValue = "${env:SCHEMA_REGISTRY:-http://localhost:2181}",
    showDefaultValue = CommandLine.Help.Visibility.ALWAYS
  )
  public URL schemaRegistry;
}
