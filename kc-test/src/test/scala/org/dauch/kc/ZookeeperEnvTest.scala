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
