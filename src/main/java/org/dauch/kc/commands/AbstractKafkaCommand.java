package org.dauch.kc.commands;

import groovyjarjarpicocli.CommandLine.Option;
import org.apache.kafka.common.TopicPartition;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import static groovyjarjarpicocli.CommandLine.Help.Visibility.ALWAYS;

public abstract class AbstractKafkaCommand extends AbstractCommand {

  @Option(
    names = {"--bootstrap-servers"},
    paramLabel = "<bootstrap-server>",
    description = "KAFKA bootstrap servers",
    defaultValue = "${env:KAFKA_BOOTSTRAP_SERVERS:-${sys:KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}}",
    showDefaultValue = ALWAYS,
    split = ","
  )
  public List<String> bootstrapServers;

  protected int compareTps(TopicPartition tp1, TopicPartition tp2) {
    var c = tp1.topic().compareTo(tp2.topic());
    if (c != 0) {
      return c;
    } else {
      return Integer.compare(tp1.partition(), tp2.partition());
    }
  }

  protected <T> ConcurrentSkipListMap<TopicPartition, T> tpMap(Map<TopicPartition, T> map) {
    var result = new ConcurrentSkipListMap<TopicPartition, T>(this::compareTps);
    map.entrySet().parallelStream().forEach(e -> result.put(e.getKey(), e.getValue()));
    return result;
  }
}
