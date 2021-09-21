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
package org.dauch.kc.commands;

import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import groovyjarjarpicocli.CommandLine.Option;
import org.apache.avro.Schema;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;
import org.apache.karaf.shell.table.ShellTable;
import org.codehaus.groovy.runtime.callsite.BooleanClosureWrapper;
import org.dauch.kc.format.OutputFormatter;
import org.dauch.kc.groovy.GroovyShellProvider;
import org.dauch.kc.kafka.DecoderKey;
import org.dauch.kc.kafka.Format;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractFetchCommand extends AbstractKafkaDataCommand {

  @Option(
    names = {"-f", "--filter"},
    description = "Groovy expression to filter incoming messages",
    defaultValue = "true"
  )
  public String filter;

  @Option(
    names = {"-p", "--projection"},
    description = "Projection expression",
    defaultValue = "[t: $r.topic(), p: $r.partition(), o: $r.offset(), ts: $r.timestamp(), k: $k, v: $v]"
  )
  public String projection;

  @Option(
    names = {"-t", "--poll-timeout"},
    description = "Poll timeout",
    defaultValue = "PT5S"
  )
  public Duration pollTimeout;

  @Option(
    names = {"-k", "--key-format"},
    description = "Key format",
    defaultValue = "${env:KC_KEY_FORMAT:-HEX}"
  )
  public Format keyFormat;

  @Option(
    names = {"-v", "--value-format"},
    description = "Value format",
    defaultValue = "${env:KC_VALUE_FORMAT:-HEX}"
  )
  public Format valueFormat;

  @Option(
    names = {"--key-schema"},
    description = "Key schema",
    defaultValue = "${env:KC_KEY_SCHEMA:-SCHEMA_REGISTRY}"
  )
  public String keySchema;

  @Option(
    names = {"--value-schema"},
    description = "Value schema",
    defaultValue = "${env:KC_VALUE_SCHEMA:-SCHEMA_REGISTRY}"
  )
  public String valueSchema;

  @Option(
    names = {"-n", "--message-count"},
    description = "Message count limit",
    defaultValue = "9223372036854775807"
  )
  public long messageCount;

  protected void printSubscription(Map<TopicPartition, OffsetAndTimestamp> offs, Map<TopicPartition, Long> endOffs) {
    var table = new ShellTable();
    table.column("Topic").alignLeft();
    table.column("Partition").alignRight();
    table.column("Offset").alignRight();
    table.column("Timestamp").alignCenter();
    table.column("End offset").alignRight();
    offs.forEach((tp, omd) -> table.addRow().addContent(
      tp.topic(),
      tp.partition(),
      omd.offset(),
      Instant.ofEpochMilli(omd.timestamp()),
      endOffs.getOrDefault(tp, -1L)
    ));
    table.print(err);
  }

  protected BooleanClosureWrapper groovyFilter(GroovyShell shell) {
    return new BooleanClosureWrapper(
      (Closure<?>) shell.evaluate("{$r, $k, $v -> $v.with {CODE}}".replace("CODE", filter))
    );
  }

  protected Closure<?> groovyProjection(GroovyShell shell) {
    return (Closure<?>) shell.evaluate("{$r, $k, $v -> $v.with {CODE}}".replace("CODE", projection));
  }

  protected Schema parseSchema(String schema) {
    switch (schema) {
      case "SCHEMA_REGISTRY": return null;
      case "STRING": return Schema.create(Schema.Type.STRING);
      case "LONG": return Schema.create(Schema.Type.LONG);
      case "INT": return Schema.create(Schema.Type.INT);
      case "DOUBLE": return Schema.create(Schema.Type.DOUBLE);
      case "FLOAT": return Schema.create(Schema.Type.FLOAT);
      case "BYTES": return Schema.create(Schema.Type.BYTES);
      case "BOOLEAN": return Schema.create(Schema.Type.BOOLEAN);
      default: return new Schema.Parser().parse(schema);
    }
  }

  protected EnumMap<DecoderKey, Object> decoderProps(Schema schema) {
    var map = new EnumMap<>(DecoderKey.class);
    map.put(DecoderKey.SCHEMA_REGISTRY, schemaRegistry);
    map.put(DecoderKey.SCHEMA, schema);
    return map;
  }

  protected class FetchState {
    public final OutputFormatter outputFormatter = new OutputFormatter();
    public final GroovyShell shell = GroovyShellProvider.defaultShell();
    public final BooleanClosureWrapper filter = groovyFilter(shell);
    public final Closure<?> projection = groovyProjection(shell);
    public final Schema keySchema = parseSchema(AbstractFetchCommand.this.keySchema);
    public final Schema valueSchema = parseSchema(AbstractFetchCommand.this.valueSchema);
    public final EnumMap<DecoderKey, Object> keyDecoderProps = decoderProps(keySchema);
    public final EnumMap<DecoderKey, Object> valueDecoderProps = decoderProps(valueSchema);
  }

  private final ArrayBlockingQueue<Runnable> taskQueue = new ArrayBlockingQueue<>(2);
  private final LinkedBlockingQueue<Throwable> exceptions = new LinkedBlockingQueue<>();

  private static final Runnable TERMINATOR = () -> {};

  private final Thread taskThread = new Thread("tasks") {
    @Override
    public void run() {
      while (!isInterrupted()) {
        try {
          var task = taskQueue.poll(1L, TimeUnit.SECONDS);
          if (task == null) {
            continue;
          }
          if (task == TERMINATOR) {
            break;
          }
          try {
            task.run();
          } catch (Throwable e) {
            exceptions.add(e);
          }
        } catch (InterruptedException e) {
          break;
        }
      }
    }
  };

  protected final class TaskContext {

    private final AtomicLong counter = new AtomicLong();

    public boolean filter(Object any) {
      return filter();
    }

    public boolean filter() {
      return counter.incrementAndGet() <= messageCount;
    }

    public boolean isCompleted() {
      return counter.get() > messageCount;
    }

    public void addTask(Runnable task) throws InterruptedException {
      reportErrors();
      taskQueue.put(task);
    }
  }

  protected interface TaskHandler {
    void doTasks(TaskContext context) throws InterruptedException;
  }

  protected void runTasks(TaskHandler taskHandler) throws InterruptedException {
    var context = new TaskContext();
    taskThread.setDaemon(true);
    taskThread.start();
    try {
      taskHandler.doTasks(context);
      taskQueue.put(TERMINATOR);
      taskThread.join();
      reportErrors();
    } catch (Throwable e) {
      taskThread.interrupt();
      throw e;
    }
  }

  protected final void reportErrors() {
    var it = exceptions.iterator();
    if (it.hasNext()) {
      var e = new IllegalStateException("Error");
      e.addSuppressed(it.next());
      while (it.hasNext()) {
        e.addSuppressed(it.next());
      }
      taskThread.interrupt();
      throw e;
    }
  }
}
