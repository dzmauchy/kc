/*
 * Copyright 2021 Dzmiter Auchynnikau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dauch.kcw.commands;

import groovy.json.JsonOutput;
import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Option;
import groovyjarjarpicocli.CommandLine.Parameters;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.dauch.kcr.commands.AbstractAdminClientCommand;
import org.dauch.kcr.util.ExceptionUtils;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;
import static java.util.zip.Deflater.BEST_COMPRESSION;
import static org.apache.kafka.clients.admin.NewPartitions.increaseTo;
import static org.apache.kafka.common.config.ConfigResource.Type.TOPIC;

@Command(
  name = "change-partitions",
  aliases = {"P"},
  description = "Change partition count command",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
)
public class ChangePartitionsCommand extends AbstractAdminClientCommand implements Callable<Integer> {

  @Parameters(
    description = "Topics"
  )
  public List<String> topics = emptyList();

  @Option(
    names = {"--retry-on-quota-violation"},
    description = "Retry on quota violation flag",
    fallbackValue = "true",
    defaultValue = "false"
  )
  public boolean retryOnQuotaViolation;

  @Option(
    names = {"--internal"},
    description = "Include internal topics flag",
    fallbackValue = "true",
    defaultValue = "false"
  )
  public boolean internal;

  @Option(
    names = {"-p", "--partitions"},
    description = "Partition count",
    required = true
  )
  public int partitions;

  @Option(
    names = {"--validation-only"},
    description = "Validation only flag",
    fallbackValue = "true",
    defaultValue = "false"
  )
  public boolean validationOnly;

  @Option(
    names = {"-g", "--group"},
    description = "Consumer group",
    defaultValue = ""
  )
  public String group = "";

  @Option(
    names = {"--skip-data"},
    description = "Skip data flag",
    fallbackValue = "true",
    defaultValue = "false"
  )
  public boolean skipData;

  private DescribeTopicsOptions describeTOpts() {
    return new DescribeTopicsOptions()
      .timeoutMs((int) timeout.toMillis())
      .includeAuthorizedOperations(false);
  }

  private CreatePartitionsOptions createPOpts() {
    return new CreatePartitionsOptions()
      .timeoutMs((int) timeout.toMillis())
      .validateOnly(validationOnly)
      .retryOnQuotaViolation(retryOnQuotaViolation);
  }

  private DescribeConfigsOptions describeCOpts() {
    return new DescribeConfigsOptions()
      .timeoutMs((int) timeout.toMillis());
  }

  private DeleteTopicsOptions deleteTOpts() {
    return new DeleteTopicsOptions()
      .timeoutMs((int) timeout.toMillis())
      .retryOnQuotaViolation(retryOnQuotaViolation);
  }

  private CreateTopicsOptions createTOpts() {
    return new CreateTopicsOptions()
      .retryOnQuotaViolation(retryOnQuotaViolation)
      .timeoutMs((int) timeout.toMillis())
      .validateOnly(validationOnly);
  }

  private ListConsumerGroupOffsetsOptions listGroupOffsetsOpts(List<TopicPartition> tps) {
    return new ListConsumerGroupOffsetsOptions()
      .topicPartitions(tps)
      .timeoutMs((int) (timeout.toMillis()));
  }

  @Override
  public Integer call() throws Exception {
    if (topics.isEmpty()) {
      verbose("Empty topic list to change partitions%n");
      return 0;
    }
    try (var client = AdminClient.create(clientProps())) {
      var topicNames = topics(client, internal, topics);
      var map = new TreeMap<String, Object>();
      for (var t : topicNames) {
        verbose("Processing topic %s%n", t);
        try {
          var prf = client.describeTopics(singleton(t), describeTOpts()).all();
          var pr = prf.get().get(t);
          if (pr != null) {
            var n = pr.partitions().size();
            if (partitions > n) {
              client.createPartitions(Map.of(t, increaseTo(partitions)), createPOpts()).all().get();
              map.put(t, true);
            } else if (partitions < n) {
              var cr = new ConfigResource(TOPIC, t);
              var r = client.describeConfigs(singleton(cr), describeCOpts()).all().get().get(cr);
              if (r != null) {
                var props = new TreeMap<String, String>();
                r.entries().forEach(e -> props.put(e.name(), e.value()));
                var replicas = (short) pr.partitions().get(0).replicas().size();
                var nt = new NewTopic(t, partitions, replicas).configs(props);
                if (skipData) {
                  verbose("Deleting topic%n");
                  client.deleteTopics(singleton(t), deleteTOpts()).all().get();
                  verbose("Creating topic%n");
                  client.createTopics(singleton(nt), createTOpts()).all().get();
                } else {
                  verbose("Copying data to a temporary file%n");
                  var file = readData(t, pr, client);
                  verbose("Deleting topic%n");
                  client.deleteTopics(singleton(t), deleteTOpts()).all().get();
                  verbose("Creating topic%n");
                  client.createTopics(singleton(nt), createTOpts()).all().get();
                  verbose("Copying data from the temporary file%n");
                  writeData(file, t);
                }
                verbose("Finished%n");
                map.put(t, true);
              } else {
                map.put(t, "NO_CONFIG_INFO");
              }
            }
          } else {
            map.put(t, "NO_DESCRIBE_INFO");
          }
        } catch (Throwable e) {
          map.put(t, ExceptionUtils.exceptionToMap(e));
          report(e);
        }
      }
      out.println(finalOutput(JsonOutput.toJson(map)));
    }
    return 0;
  }

  private File readData(String topic, TopicDescription td, AdminClient client) throws Exception {
    var props = new Properties();
    props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", bootstrapServers));
    props.setProperty(ConsumerConfig.CLIENT_ID_CONFIG, "kc");
    props.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    props.setProperty(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");
    props.setProperty(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
    props.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "2048");
    props.setProperty(ConsumerConfig.RECEIVE_BUFFER_CONFIG, Integer.toString(1 << 20));
    var tps = td.partitions().parallelStream()
      .map(d -> new TopicPartition(topic, d.partition()))
      .collect(toList());
    try (var c = new KafkaConsumer<>(props, new ByteArrayDeserializer(), new ByteArrayDeserializer())) {
      if (group.isBlank()) {
        c.assign(tps);
      } else {
        var offsets = client.listConsumerGroupOffsets(group, listGroupOffsetsOpts(tps))
          .partitionsToOffsetAndMetadata()
          .get();
        c.assign(tps);
        for (var tp : tps) {
          var om = offsets.get(tp);
          if (om == null) {
            c.seekToBeginning(singleton(tp));
          } else {
            c.seek(tp, om);
          }
        }
      }
      var file = File.createTempFile("kc", ".tmp");
      try (var os = new DataOutputStream(
        new GZIPOutputStream(new FileOutputStream(file), 65536) {{def.setLevel(BEST_COMPRESSION);}}
      )) {
        while (true) {
          var records = c.poll(Duration.ofSeconds(5L));
          if (records.isEmpty()) {
            var endOffsets = c.endOffsets(tps);
            if (tps.stream().allMatch(tp -> c.position(tp) >= endOffsets.getOrDefault(tp, 0L))) {
              break;
            }
          } else {
            for (var r : records) {
              os.writeBoolean(true);
              var headers = r.headers().toArray();
              os.writeInt(headers.length);
              for (var h : headers) {
                os.writeUTF(h.key());
                write(h.value(), os);
              }
              os.writeLong(r.timestamp());
              write(r.key(), os);
              write(r.value(), os);
            }
            os.writeBoolean(false);
          }
        }
      }
      return file;
    }
  }

  private void write(byte[] data, DataOutputStream os) throws IOException {
    if (data == null) {
      os.writeInt(-1);
    } else {
      os.writeInt(data.length);
      os.write(data);
    }
  }

  private void writeData(File file, String topic) throws Exception {
    var props = new Properties();
    props.setProperty(ProducerConfig.CLIENT_ID_CONFIG, "kc");
    props.setProperty(ProducerConfig.ACKS_CONFIG, "all");
    props.setProperty(ProducerConfig.SEND_BUFFER_CONFIG, Integer.toString(1 << 20));
    props.setProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");
    props.setProperty(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1");
    props.setProperty(ProducerConfig.LINGER_MS_CONFIG, "1000");
    props.setProperty(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(1 << 20));
    try (
      var p = new KafkaProducer<>(props, new ByteArraySerializer(), new ByteArraySerializer());
      var is = new DataInputStream(new GZIPInputStream(new FileInputStream(file), 1 << 20))
    ) {
      var futures = new HashMap<CompletableFuture<RecordMetadata>, Boolean>();
      var exceptions = new ConcurrentLinkedQueue<Exception>();
      while (is.readBoolean()) {
        var hSize = is.readInt();
        var headers = new LinkedList<Header>();
        for (int i = 0; i < hSize; i++) {
          headers.add(new RecordHeader(is.readUTF(), read(is)));
        }
        var timestamp = is.readLong();
        var key = read(is);
        var value = read(is);
        var f = new CompletableFuture<RecordMetadata>();
        synchronized (futures) {
          futures.put(f, true);
        }
        var rec = new ProducerRecord<>(topic, null, timestamp, key, value, headers);
        p.send(rec, (md, e) -> {
          if (e == null) {
            f.complete(md);
          } else {
            f.completeExceptionally(e);
            exceptions.add(e);
          }
          synchronized (futures) {
            futures.remove(f);
            futures.notify();
          }
        });
      }
      synchronized (futures) {
        while (!futures.isEmpty() && exceptions.isEmpty()) {
          futures.wait(1000L);
        }
      }
      var e = exceptions.poll();
      if (e != null) {
        exceptions.removeIf(x -> {
          e.addSuppressed(x);
          return true;
        });
        throw e;
      }
    }
  }

  private byte[] read(DataInputStream is) throws IOException {
    var size = is.readInt();
    return size == -1 ? null : is.readNBytes(size);
  }
}
