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
package org.dauch.test.env

import org.apache.zookeeper.client.ZKClientConfig
import org.apache.zookeeper.metrics.impl.NullMetricsProvider
import org.apache.zookeeper.server.ServerMetrics
import org.apache.zookeeper.server.quorum.QuorumPeer
import org.apache.zookeeper.server.quorum.QuorumPeer.{QuorumServer, ServerState}
import org.apache.zookeeper.{Watcher, ZooKeeper}

import java.lang.Thread.sleep
import java.lang.Long as JLong
import java.net.{InetSocketAddress, ServerSocket}
import java.nio.file.{Files, Path}
import java.util.Map as JMap
import scala.collection.immutable.LongMap
import scala.concurrent.TimeoutException
import scala.jdk.CollectionConverters.*
import scala.util.Using.resource

trait ZookeeperEnv extends Env {

  def zkConf: ZookeeperEnv.Conf = ZookeeperEnv.Conf()

  private var zkQuorum = LongMap.empty[QuorumServer]
  private var zkPeers = LongMap.empty[QuorumPeer]
  private var zkDirectory: Path = _

  protected def zkConnectionText: String = zkPeers
    .map { case (_, p) => s"localhost:${p.getClientPort}" }
    .mkString(",")

  override def beforeAll(): Unit = {
    super.beforeAll()
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
      val peer = new QuorumPeer(q, snap.toFile, log.toFile, 0, 3, id, 3000, 5, 0, 5)
      peer.setQuorumListenOnAllIPs(true)
      peer.setSyncEnabled(false)
      peer.setUsePortUnification(true)
      peer.setClientPortListenBacklog(1)
      peer.setSslQuorum(false)
      peer.initialize()
      peer.start()
      id -> peer
    }
    while (zkPeers.exists(_._2.getPeerState == ServerState.LOOKING)) {
      sleep(100L)
    }
    logger.info("Zookeeper cluster started: {}", zkConnectionText)
  }

  override def afterAll(): Unit = {
    release { resources =>
      resources.register(super.afterAll())
      resources(zkDirectory)
      if (zkPeers != null) {
        zkPeers.foreachEntry((_, p) => p.shutdown())
        zkPeers.foreachEntry((_, p) => p.join())
      }
    }
    logger.info("Zookeeper cluster shutdown")
  }

  protected def withZookeeper[R](code: ZooKeeper => R)(implicit watcher: Watcher = _ => ()): R = {
    val clientConfig = new ZKClientConfig()
    clientConfig.setProperty(ZKClientConfig.SECURE_CLIENT, "false")
    clientConfig.setProperty(ZKClientConfig.ENABLE_CLIENT_SASL_KEY, "false")
    resource(new ZooKeeper(zkConnectionText, 10_000, watcher, clientConfig)) { zk =>
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
