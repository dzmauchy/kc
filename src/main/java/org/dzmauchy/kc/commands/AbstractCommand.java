package org.dzmauchy.kc.commands;

import groovyjarjarpicocli.CommandLine.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;

public abstract class AbstractCommand {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  @Option(
    names = {"--quiet"},
    description = "Quiet mode",
    fallbackValue = "true",
    defaultValue = "false"
  )
  public boolean quiet;

  public PrintStream out = System.out;

  public PrintStream err = System.err;
}
