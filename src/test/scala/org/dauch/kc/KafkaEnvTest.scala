package org.dauch.kc

import org.apache.kafka.clients.producer.ProducerRecord
import org.dauch.test.TestWithEnv
import org.dauch.test.env.KafkaEnv
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
