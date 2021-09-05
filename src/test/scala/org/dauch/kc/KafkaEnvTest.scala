package org.dauch.kc

import org.dauch.test.TestWithEnv
import org.dauch.test.env.KafkaEnv
import org.junit.jupiter.api.{Tag, Test}

@Tag("normal")
final class KafkaEnvTest extends TestWithEnv with KafkaEnv {
  @Test
  def run(): Unit = {

  }
}
