package org.dauch.kc

import org.apache.zookeeper.CreateMode.PERSISTENT
import org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE
import org.dauch.test.TestWithEnv
import org.dauch.test.env.ZookeeperEnv
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.{Tag, Test}

@Tag("normal")
final class ZookeeperEnvTest extends TestWithEnv with ZookeeperEnv {
  @Test
  def run(): Unit = {
    withZookeeper { zk =>
      val expected = Array[Byte](8, 9, 10)
      val path = zk.create("/mypath", expected, OPEN_ACL_UNSAFE, PERSISTENT)
      val actual = zk.getData(path, false, null)
      assertArrayEquals(expected, actual)
    }
  }
}
