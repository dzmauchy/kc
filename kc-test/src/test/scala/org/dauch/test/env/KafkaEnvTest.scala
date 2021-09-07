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

import org.apache.kafka.clients.producer.ProducerRecord
import org.dauch.test.TestWithEnv
import org.dauch.test.env.KafkaEnv.TopicsQuery
import org.dauch.test.implicits.{DeserializerImplicits, SerializerImplicits}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Tag, Test}

@Tag("normal")
final class KafkaEnvTest
  extends TestWithEnv
    with KafkaEnv
    with SerializerImplicits
    with DeserializerImplicits {

  @Test
  def run(): Unit = {
    produce(64) { producer =>
      producer.produce(new ProducerRecord("abc", "key", "value"))
    }
    consume(TopicsQuery("abc")) { fetcher =>
      fetcher.read[String, String].from { it =>
        eventually {
          assertEquals(List("key" -> "value"), it.map(r => r.key() -> r.value()).toList)
        }
      }
    }
  }
}
