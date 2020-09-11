package org.dzmauchy.kc.commands;

import groovyjarjarpicocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(
  description = "Kafka CLI",
  showDefaultValues = true,
  showEndOfOptionsDelimiterInUsageHelp = true,
  mixinStandardHelpOptions = true
)
public class MainCommand implements Callable<Integer> {

  @Override
  public Integer call() throws Exception {
    return null;
  }
}