package org.ku.kc.commands;

import groovyjarjarpicocli.CommandLine.Option;
import org.apache.kafka.common.TopicPartition;

import java.util.Comparator;
import java.util.List;

import static groovyjarjarpicocli.CommandLine.Help.Visibility.ALWAYS;

public abstract class AbstractKafkaCommand extends AbstractCommand {

  @Option(
    names = {"--bootstrap-servers"},
    paramLabel = "<bootstrap-server>",
    description = "KAFKA bootstrap servers",
    defaultValue = "${env:KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}",
    showDefaultValue = ALWAYS,
    split = ","
  )
  public List<String> bootstrapServers;

  protected Comparator<TopicPartition> tpc() {
    return Comparator
      .comparing(TopicPartition::topic)
      .thenComparingInt(TopicPartition::partition);
  }
}
