package org.dauch.kc.logging

import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.logging.{Handler, LogRecord}
import scala.util.chaining.scalaUtilChainingOps

object TestLoggingHandler extends Handler {

  setFilter(TestLoggingFilter)
  setFormatter(new DefaultFormatter)

  private val logsDir = Path.of("build").resolve("logs").tap(Files.createDirectories(_))
  private val kafkaFile = logsDir.resolve("kafka.log")
  private val zkFile = logsDir.resolve("zk.log")
  private val kafkaChannel = new PrintStream(kafkaFile.toFile, UTF_8)
  private val zkChannel = new PrintStream(zkFile.toFile, UTF_8)

  override def publish(record: LogRecord): Unit = {
    if (isLoggable(record)) {
      val msg = getFormatter.format(record)
      record.getLoggerName match {
        case s"org.apache.zookeeper$_" => zkChannel.println(msg)
        case s"kafka.$_" | s"org.apache.kafka$_" | "state.change.logger" => kafkaChannel.println(msg)
        case _ => System.out.println(msg)
      }
    }
  }

  override def flush(): Unit = {
    kafkaChannel.flush()
    zkChannel.flush()
  }

  override def close(): Unit = {
    kafkaChannel.close()
    zkChannel.close()
  }
}
