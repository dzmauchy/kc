package org.dauch.kc;

import groovyjarjarpicocli.CommandLine;
import org.dauch.kc.commands.MainCommand;
import org.dauch.kc.logging.DefaultFormatter;
import org.dauch.kc.version.KcVersionProvider;

import java.util.logging.FileHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Kc {

  public static void main(String... args) throws Exception {
    initLogging();
    var commandLine = new CommandLine(new MainCommand());
    commandLine.getCommandSpec().versionProvider(new KcVersionProvider());
    int code = commandLine.execute(args);
    System.exit(code);
  }

  private static void initLogging() throws Exception {
    LogManager.getLogManager().reset();
    var handler = new FileHandler("%t/kc-%g-%u.log", 0L, 1, false);
    handler.setFormatter(new DefaultFormatter());
    Logger.getLogger("").addHandler(handler);
  }
}
