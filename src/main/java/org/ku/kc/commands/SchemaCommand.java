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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

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
  public List<String> tpos;

  @Override
  public Integer call() throws Exception {
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
              out.println(JsonOutput.prettyPrint(schemaText));
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
