package org.dzmauchy.kc;

import groovyjarjarpicocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
  aliases = {"q"},
  description = "Kafka CLI",
  showDefaultValues = true,
  showEndOfOptionsDelimiterInUsageHelp = true,
  mixinStandardHelpOptions = true
)
public class QueryCommand implements Callable<Integer> {
  @Override
  public Integer call() throws Exception {
    return null;
  }
}
