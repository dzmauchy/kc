package org.dzmauchy.kc;

import groovyjarjarpicocli.CommandLine;

public class Kc {
  public static void main(String... args) throws Exception {
    var commandLine = new CommandLine(new QueryCommand())
      .addSubcommand("offsets", new OffsetsCommand(), "o");
    int code = commandLine.execute(args);
    System.exit(code);
  }
}
