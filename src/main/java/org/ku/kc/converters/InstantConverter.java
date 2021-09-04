package org.ku.kc.converters;

import groovyjarjarpicocli.CommandLine;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;

public class InstantConverter implements CommandLine.ITypeConverter<Instant> {

  //@formatter:off
  private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
    .optionalStart()
      .appendValue(ChronoField.YEAR, 4)
      .appendLiteral('-')
      .appendValue(ChronoField.MONTH_OF_YEAR, 2)
      .appendLiteral('-')
      .appendValue(ChronoField.DAY_OF_MONTH, 2)
      .appendLiteral('T')
      .appendValue(ChronoField.HOUR_OF_DAY, 2)
      .optionalStart()
        .appendLiteral(':')
        .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
        .optionalStart()
          .appendLiteral(':')
          .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
        .optionalEnd()
      .optionalEnd()
    .optionalEnd()
    .optionalStart()
      .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
      .appendValue(ChronoField.YEAR, 4)
      .appendLiteral('-')
      .appendValue(ChronoField.MONTH_OF_YEAR, 2)
      .appendLiteral('-')
      .appendValue(ChronoField.DAY_OF_MONTH, 2)
      .appendLiteral('T')
    .optionalEnd()
    .optionalStart()
      .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
      .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
      .appendValue(ChronoField.YEAR, 4)
      .appendLiteral('-')
      .appendValue(ChronoField.MONTH_OF_YEAR, 2)
      .appendLiteral('T')
    .optionalEnd()
    .optionalStart()
      .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
      .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
      .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
      .appendValue(ChronoField.YEAR, 4)
      .appendLiteral('T')
    .optionalEnd()
    .toFormatter();
//@formatter:on

  @Override
  public Instant convert(String s) {
    switch (s) {
      case "now": return Instant.now();
      case "today": return LocalDateTime.now(UTC).truncatedTo(DAYS).toInstant(UTC);
      case "yesterday": return LocalDateTime.now(UTC).truncatedTo(DAYS).minusDays(1L).toInstant(UTC);
      case "tomorrow": return LocalDateTime.now(UTC).truncatedTo(DAYS).plusDays(1L).toInstant(UTC);
      case "start": return Instant.EPOCH;
      default: {
        if (s.startsWith("-")) {
          var dur = Duration.parse(s.substring(1));
          return Instant.now().minus(dur);
        } else {
          return LocalDateTime.parse(s, FORMATTER).toInstant(UTC);
        }
      }
    }
  }
}
