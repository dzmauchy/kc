package org.dauch.test.env

import kafka.server.{KafkaConfig, KafkaServer}
import org.apache.kafka.common.network.ListenerName
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.metadata.BrokerState

import java.nio.file.{Files, Path}
import java.rmi.server.UID
import java.util.Properties

trait KafkaEnv extends ZookeeperEnv {

  def kafkaConf: KafkaEnv.Conf = KafkaEnv.Conf()

  private var kafkaServers: IndexedSeq[KafkaServer] = _
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
      props.setProperty(KafkaConfig.ZkSyncTimeMsProp, "100")
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
      if (kafkaServers != null) {
        kafkaServers.foreach(s => resources.register(s.awaitShutdown()))
        kafkaServers.foreach(s => resources.register(s.shutdown()))
      }
    }
  }

  def kafkaBootstrapServers: String = kafkaServers
    .map { s =>
      val port = s.boundPort(ListenerName.forSecurityProtocol(SecurityProtocol.PLAINTEXT))
      s"localhost:$port"
    }
    .mkString(",")
}

object KafkaEnv {
  case class Conf(nodes: Int = 3)
}
