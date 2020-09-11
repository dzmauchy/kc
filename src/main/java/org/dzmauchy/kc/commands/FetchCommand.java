package org.dzmauchy.kc.commands;

import groovyjarjarpicocli.CommandLine;
import groovyjarjarpicocli.CommandLine.Command;
import org.dzmauchy.kc.converters.InstantConverter;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
  name = "fetch",
  aliases = {"f"},
  description = "Fetch command",
  mixinStandardHelpOptions = true
)
public class FetchCommand extends AbstractKafkaCommand implements Callable<Integer> {

  @CommandLine.Option(
    names = {"-F", "--from"},
    description = "Date to fetch since",
    converter = InstantConverter.class,
    defaultValue = "today"
  )
  public Instant from;

  @CommandLine.Option(
    names = {"-T", "--to"},
    description = "Date to fetch until",
    converter = InstantConverter.class,
    defaultValue = "now"
  )
  public Instant to;

  @CommandLine.Option(
    names = {"-f", "--filter"},
    description = "Groovy expression to filter incoming messages",
    defaultValue = "true"
  )
  public String filter;

  @CommandLine.Parameters(
    description = "Input topics"
  )
  public List<String> topics;

  @Override
  public Integer call() throws Exception {
    return null;
  }
}
