package org.dzmauchy.kc;

import groovyjarjarpicocli.CommandLine;
import org.dzmauchy.kc.commands.MainCommand;

public class Kc {
  public static void main(String... args) throws Exception {
    var commandLine = new CommandLine(new MainCommand());
    int code = commandLine.execute(args);
    System.exit(code);
  }
}
