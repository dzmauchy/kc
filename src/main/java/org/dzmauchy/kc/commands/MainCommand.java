package org.dzmauchy.kc.commands;

import groovyjarjarpicocli.CommandLine;
import groovyjarjarpicocli.CommandLine.Command;
import org.dzmauchy.kc.version.KcVersionProvider;

import java.util.concurrent.Callable;

@Command(
  description = "Kafka command-line utility",
  versionProvider = KcVersionProvider.class,
  subcommands = {
    FetchCommand.class,
    OffsetsCommand.class
  }
)
public class MainCommand implements Callable<Integer> {
  @Override
  public Integer call() {
    CommandLine.usage(this, System.out);
    return 0;
  }
}
