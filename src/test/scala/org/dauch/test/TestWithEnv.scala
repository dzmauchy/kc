package org.dauch.test

import org.dauch.test.env.Env
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.{AfterAll, BeforeAll, TestInstance}

@TestInstance(Lifecycle.PER_CLASS)
abstract class TestWithEnv {
  this: Env =>

  @BeforeAll def beforeAll(): Unit = before()
  @AfterAll def afterAll(): Unit = after()
}
