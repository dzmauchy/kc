package org.dauch.test.env

import org.apache.zookeeper.metrics.impl.NullMetricsProvider
import org.apache.zookeeper.server.ServerMetrics
import org.apache.zookeeper.server.quorum.QuorumPeer
import org.apache.zookeeper.server.quorum.QuorumPeer.{QuorumServer, ServerState}
import org.apache.zookeeper.{Watcher, ZooKeeper}

import java.lang.Thread.sleep
import java.lang.{Long => JLong}
import java.net.{InetSocketAddress, ServerSocket}
import java.nio.file.{Files, Path}
import java.util.{Map => JMap}
import scala.collection.immutable.LongMap
import scala.concurrent.TimeoutException
import scala.jdk.CollectionConverters._
import scala.util.Using.resource

trait ZookeeperEnv extends Env {

  def zkConf: ZookeeperEnv.Conf = ZookeeperEnv.Conf()

  private var zkQuorum = LongMap.empty[QuorumServer]
  private var zkPeers = LongMap.empty[QuorumPeer]
  private var zkDirectory: Path = _

  protected def zkConnectionText: String = zkPeers
    .map { case (_, p) => s"localhost:${p.getClientPort}" }
    .mkString(",")

  override def before(): Unit = {
    super.before()
    System.setProperty("zookeeper.admin.enableServer", "false")
    System.setProperty("zookeeper.leaderConnectDelayDuringRetryMs", "60000")
    ServerMetrics.metricsProviderInitialized(NullMetricsProvider.INSTANCE)
    val conf = this.zkConf
    val sockets = (1 to conf.nodes).map(id => id.toLong -> (new ServerSocket(0) -> new ServerSocket(0)))
    zkQuorum = LongMap.from(
      sockets.map { case (id, (s1, s2)) => id ->
        new QuorumServer(id,
          s1.getLocalSocketAddress.asInstanceOf[InetSocketAddress],
          s2.getLocalSocketAddress.asInstanceOf[InetSocketAddress]
        )
      }
    )
    sockets.foreach { case (_, (s1, s2)) => s1.close(); s2.close() }
    zkDirectory = Files.createTempDirectory("zoo")
    zkPeers = zkQuorum.map { case (id, _) =>
      val log = zkDirectory.resolve("log-" + id)
      val snap = zkDirectory.resolve("snap-" + id)
      val q = zkQuorum.asJava.asInstanceOf[JMap[JLong, QuorumServer]]
      val peer = new QuorumPeer(q, snap.toFile, log.toFile, 0, 3, id, 100, 1000, 100, 100)
      peer.initialize()
      peer.start()
      id -> peer
    }
    while (zkPeers.exists(_._2.getPeerState == ServerState.LOOKING)) {
      sleep(100L)
    }
  }

  override def after(): Unit = {
    release { resources =>
      resources.register(super.after())
      resources(zkDirectory)
      if (zkPeers != null) {
        zkPeers.foreachEntry((_, p) => p.shutdown())
        zkPeers.foreachEntry((_, p) => p.join())
      }
    }
  }

  protected def withZookeeper[R](code: ZooKeeper => R)(implicit watcher: Watcher = _ => ()): R = {
    resource(new ZooKeeper(zkConnectionText, 10_000, watcher)) { zk =>
      val startTime = System.nanoTime()
      while (!zk.getState.isConnected) {
        if (System.nanoTime() - startTime > 60_000_000_000L) {
          throw new TimeoutException("Client is still not connected")
        }
        sleep(10L)
      }
      code(zk)
    }
  }
}

object ZookeeperEnv {
  case class Conf(nodes: Int = 3)
}
