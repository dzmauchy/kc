package org.dzmauchy.kc;

import groovyjarjarpicocli.CommandLine;
import org.dzmauchy.kc.commands.FetchCommand;
import org.dzmauchy.kc.commands.OffsetsCommand;

public class Kc {
  public static void main(String... args) throws Exception {
    var commandLine = new CommandLine(new QueryCommand())
      .addSubcommand(new FetchCommand())
      .addSubcommand(new OffsetsCommand());
    int code = commandLine.execute(args);
    System.exit(code);
  }
}
