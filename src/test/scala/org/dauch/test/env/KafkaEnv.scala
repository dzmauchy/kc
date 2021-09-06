package org.dauch.test.env

import com.typesafe.scalalogging.StrictLogging
import kafka.server.{KafkaConfig, KafkaServer}
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.network.ListenerName
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization._
import org.apache.kafka.metadata.BrokerState
import org.dauch.test.env.KafkaEnv._

import java.lang.invoke.VarHandle
import java.nio.file.{Files, Path}
import java.rmi.server.UID
import java.time.Duration
import java.util.Properties
import java.util.concurrent.Future
import java.util.regex.Pattern
import scala.jdk.CollectionConverters._
import scala.util.Using.resource

trait KafkaEnv extends ZookeeperEnv {

  def kafkaConf: KafkaEnv.Conf = KafkaEnv.Conf()

  private var kafkaServers = IndexedSeq.empty[KafkaServer]
  private var kafkaDirectory: Path = _

  override def before(): Unit = {
    super.before()
    val conf = this.kafkaConf
    kafkaDirectory = Files.createTempDirectory("kafka")
    kafkaServers = (0 until conf.nodes).map { id =>
      val logDir = kafkaDirectory.resolve("log-" + id)
      Files.createDirectory(logDir)
      val props = new Properties()
      props.setProperty(KafkaConfig.BrokerIdProp, id.toString)
      props.setProperty(KafkaConfig.PortProp, "0")
      props.setProperty(KafkaConfig.LogDirProp, logDir.toString)
      props.setProperty(KafkaConfig.NumPartitionsProp, "10")
      props.setProperty(KafkaConfig.ZkConnectProp, zkConnectionText)
      props.setProperty(KafkaConfig.ZkSyncTimeMsProp, "1000")
      props.setProperty(KafkaConfig.ZkConnectionTimeoutMsProp, "30000")
      props.setProperty(KafkaConfig.ZkEnableSecureAclsProp, "false")
      props.setProperty(KafkaConfig.ZkSslClientEnableProp, "false")
      props.setProperty(KafkaConfig.OffsetsTopicReplicationFactorProp, conf.nodes.toString)
      props.setProperty(KafkaConfig.AutoCreateTopicsEnableProp, "true")
      props.setProperty(KafkaConfig.DefaultReplicationFactorProp, conf.nodes.toString)
      props.setProperty(KafkaConfig.ControlledShutdownEnableProp, "false")
      props.setProperty(KafkaConfig.DeleteTopicEnableProp, "true")
      props.setProperty(KafkaConfig.RackProp, new UID().toString)
      val server = new KafkaServer(new KafkaConfig(props))
      server.startup()
      server
    }
    eventually {
      assume(kafkaServers.forall(_.brokerState.get() == BrokerState.RUNNING))
    }
    logger.info("Started KAFKA: {}", kafkaBootstrapServers)
  }

  override def after(): Unit = {
    release { resources =>
      resources.register(super.after())
      resources(kafkaDirectory)
      kafkaServers.foreach(s => resources.register(s.awaitShutdown()))
      kafkaServers.foreach(s => resources.register(s.shutdown()))
    }
    logger.info("KAFKA shutdown")
  }

  def kafkaBootstrapServers: String = kafkaServers
    .map { s =>
      val port = s.boundPort(ListenerName.forSecurityProtocol(SecurityProtocol.PLAINTEXT))
      s"localhost:$port"
    }
    .mkString(",")

  def consume[R](query: ConsumeQuery, tx: Boolean = false, log: Boolean = true)(f: Fetcher => R): R = {
    val props = new Properties()
    props.setProperty(ConsumerConfig.CLIENT_ID_CONFIG, new UID().toString)
    props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, new UID().toString)
    props.setProperty(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "true")
    props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers)
    props.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    if (tx) {
      props.setProperty(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed")
    }
    release { $ =>
      val c = $(new KafkaConsumer(props, new ByteArrayDeserializer, new ByteArrayDeserializer))
      query match {
        case TopicsQuery(topics@_*) => c.subscribe(topics.asJavaCollection)
        case TopicPatternQuery(pattern) => c.subscribe(pattern)
        case AssignQuery(tps@_*) => c.assign(tps.map { case (t, p) => new TopicPartition(t, p) }.asJava)
        case SeekQuery(tpos@_*) =>
          c.assign(tpos.map { case (t, p, _) => new TopicPartition(t, p) }.asJava)
          tpos.foreach { case (t, p, o) => c.seek(new TopicPartition(t, p), o) }
      }
      val fetcher = $(new FetcherImpl(c, log))
      fetcher.start()
      f(fetcher)
    }
  }

  def produce[R](batchSize: Int, linger: Int = 100, tx: Boolean = false, log: Boolean = true)(f: Producer => R): R = {
    val props = new Properties()
    props.setProperty(ProducerConfig.CLIENT_ID_CONFIG, new UID().toString)
    props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers)
    props.setProperty(ProducerConfig.BATCH_SIZE_CONFIG, batchSize.toString)
    props.setProperty(ProducerConfig.LINGER_MS_CONFIG, linger.toString)
    props.setProperty(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, "900000")
    if (tx) {
      props.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
      props.setProperty(ProducerConfig.TRANSACTIONAL_ID_CONFIG, new UID().toString)
    }
    resource(new KafkaProducer(props, new ByteArraySerializer, new ByteArraySerializer)) { p =>
      if (tx) {
        p.initTransactions()
      }
      f(new ProducerImpl(p, log))
    }
  }
}

object KafkaEnv {
  case class Conf(nodes: Int = 3)

  type Records = ConsumerRecords[Array[Byte], Array[Byte]]
  type Record = ConsumerRecord[Array[Byte], Array[Byte]]
  type RawConsumer = KafkaConsumer[Array[Byte], Array[Byte]]

  sealed trait ConsumeQuery
  case class TopicsQuery(topics: String*) extends ConsumeQuery
  case class AssignQuery(topicPartitions: (String, Int)*) extends ConsumeQuery
  case class TopicPatternQuery(pattern: Pattern) extends ConsumeQuery
  case class SeekQuery(tpos: (String, Int, Long)*) extends ConsumeQuery

  trait Fetcher {
    def fetch[R](f: Iterable[Record] => R): R
    def seek(tpos: (String, Int, Long)*): Unit
    def read[K: Deserializer, V: Deserializer]: SpecificReader[K, V] = {
      val kDeserializer = implicitly[Deserializer[K]]
      val vDeserializer = implicitly[Deserializer[V]]
      new SpecificReader[K, V] {
        override def from[R](f: Iterable[ConsumerRecord[K, V]] => R): R = {
          fetch { it =>
            f(new Iterable[ConsumerRecord[K, V]] {
              override def iterator: Iterator[ConsumerRecord[K, V]] = {
                it.iterator.map { r =>
                  new ConsumerRecord(
                    r.topic(),
                    r.partition(),
                    r.offset(),
                    r.timestamp(),
                    r.timestampType(),
                    null,
                    r.serializedKeySize(),
                    r.serializedValueSize(),
                    kDeserializer.deserialize(r.topic(), r.key()),
                    vDeserializer.deserialize(r.topic(), r.value()),
                    r.headers(),
                    r.leaderEpoch()
                  )
                }
              }
            })
          }
        }
      }
    }
  }

  trait SpecificReader[K, V] {
    def from[R](f: Iterable[ConsumerRecord[K, V]] => R): R
  }

  private final class FetcherImpl(consumer: RawConsumer, log: Boolean)
    extends Fetcher
      with AutoCloseable
      with StrictLogging {

    private var queue = Vector.empty[Record]
    private val thread = new Thread(() => run())

    @volatile private var active = true
    @volatile private var error: Throwable = _

    override def fetch[R](f: Iterable[Record] => R): R = {
      f(new Iterable[Record] {
        override def iterator: Iterator[Record] = {
          VarHandle.acquireFence()
          queue.iterator
        }
      })
    }
    override def seek(tpos: (String, Int, Long)*): Unit = {
      for ((topic, partition, offset) <- tpos) {
        val tp = new TopicPartition(topic, partition)
        consumer.synchronized {
          consumer.seek(tp, offset)
        }
      }
    }
    private def run(): Unit = {
      try {
        while (active && !thread.isInterrupted) {
          val records = consumer.synchronized(consumer.poll(Duration.ofSeconds(1L)))
          val vec = Vector.from(records.iterator().asScala)
          queue = queue ++ vec
          VarHandle.releaseFence()
          if (log) {
            vec.foreach(logger.info("Received {}", _))
          }
        }
      } catch {
        case e: Throwable => error = e
      }
    }
    def start(): Unit = thread.start()
    override def close(): Unit = {
      active = false
      thread.join()
    }
  }

  trait Producer {
    def produce[K, V](record: ProducerRecord[K, V])(implicit ks: Serializer[K], vs: Serializer[V]): Future[RecordMetadata]
    def doInTx[T](code: => T): T
  }

  private final class ProducerImpl(producer: KafkaProducer[Array[Byte], Array[Byte]], log: Boolean) extends Producer with StrictLogging {
    override def produce[K: Serializer, V: Serializer](record: ProducerRecord[K, V]): Future[RecordMetadata] = {
      val r = new ProducerRecord(
        record.topic(),
        record.partition(),
        record.timestamp(),
        implicitly[Serializer[K]].serialize(record.topic(), record.headers(), record.key()),
        implicitly[Serializer[V]].serialize(record.topic(), record.headers(), record.value()),
        record.headers()
      )
      producer.send(r, (metadata: RecordMetadata, exception: Exception) => {
        if (log) {
          if (exception != null) {
            logger.error("Unable to produce {}", metadata)
          } else {
            logger.info("Produced {}", metadata)
          }
        }
      })
    }
    override def doInTx[T](code: => T): T = {
      producer.beginTransaction()
      if (log) {
        logger.info("Tx started")
      }
      try {
        val r = code
        producer.commitTransaction()
        if (log) {
          logger.info("Tx committed")
        }
        r
      } catch {
        case e: Throwable =>
          try {
            producer.abortTransaction()
            if (log) {
              logger.info("Tx aborted")
            }
          } catch {
            case x: Throwable => e.addSuppressed(x)
          }
          throw e
      }
    }
  }
}
