package org.dauch.kc.commands;

import groovyjarjarpicocli.CommandLine.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

public abstract class AbstractCommand {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  protected final DateTimeFormatter defaultTimeFormatter = new DateTimeFormatterBuilder()
    .appendValue(ChronoField.YEAR, 4)
    .appendLiteral("-")
    .appendValue(ChronoField.MONTH_OF_YEAR, 2)
    .appendLiteral('-')
    .appendValue(ChronoField.DAY_OF_MONTH, 2)
    .appendLiteral('T')
    .appendValue(ChronoField.HOUR_OF_DAY, 2)
    .appendLiteral(':')
    .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
    .appendLiteral(':')
    .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
    .appendLiteral('.')
    .appendValue(ChronoField.MILLI_OF_SECOND, 3)
    .toFormatter();

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
