package org.ku.kc.commands;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Parameters;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@Command(
  name = "schema",
  aliases = {"S"},
  description = "Get schema command",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
)
public class SchemaCommand extends AbstractKafkaDataCommand implements Callable<Integer> {

  @Parameters(
    description = "Input topic:partition:offset pairs"
  )
  public List<String> tpos = emptyList();

  @Override
  public Integer call() {
    var slurper = new JsonSlurper();
    try (final var consumer = new KafkaConsumer<>(consumerProps(), BAD, BAD)) {
      out.println("[");
      boolean first = true;
      for (var tpo : tpos) {
        var parts = tpo.split(":");
        if (parts.length < 2) {
          throw new IllegalArgumentException(String.join(":", parts));
        }
        var assignment = singletonList(new TopicPartition(parts[0], Integer.parseInt(parts[1])));
        consumer.assign(assignment);
        switch (parts.length) {
          case 2:
            consumer.seekToBeginning(assignment);
            break;
          case 3:
            consumer.seek(assignment.get(0), Long.parseLong(parts[2]));
            break;
        }
        var batch = consumer.poll(Duration.ofSeconds(10L));
        if (!batch.isEmpty()) {
          var record = batch.iterator().next();
          int schemaId = ByteBuffer.wrap(record.value(), 1, 4).getInt();
          try {
            var url = schemaRegistry.resolve("schemas/ids/" + schemaId).toURL();
            try (var is = url.openStream()) {
              var json = (Map<?, ?>) slurper.parse(is, "UTF-8");
              var schemaText = json.get("schema").toString();
              if (first) {
                first = false;
              } else {
                out.println(",");
              }
              var map = new LinkedHashMap<String, Object>(4 + json.size() - 1);
              map.put("topic", record.topic());
              map.put("partition", record.partition());
              map.put("offset", record.offset());
              map.put("id", schemaId);
              json.forEach((k, v) -> {
                var ks = k.toString();
                if (!"schema".equals(ks)) {
                  map.put(ks, v);
                }
              });
              map.put("schema", slurper.parse(schemaText.toCharArray()));
              out.println(JsonOutput.prettyPrint(JsonOutput.toJson(map)));
            }
          } catch (IOException e) {
            throw new UncheckedIOException("Unable to decode schema for id = " + schemaId, e);
          }
        }
      }
      out.println("]");
    }
    return 0;
  }
}
