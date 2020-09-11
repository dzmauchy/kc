package org.dzmauchy.kc.commands;

import groovyjarjarpicocli.CommandLine.Option;

import java.net.URL;

import static groovyjarjarpicocli.CommandLine.Help.Visibility.ALWAYS;

public class AbstractKafkaDataCommand extends AbstractKafkaCommand {

  @Option(
    names = {"--schema-registry"},
    paramLabel = "<schema-registry-url>",
    description = "Schema registry URL",
    defaultValue = "${env:SCHEMA_REGISTRY:-http://localhost:2181}",
    showDefaultValue = ALWAYS
  )
  public URL schemaRegistry;

}
