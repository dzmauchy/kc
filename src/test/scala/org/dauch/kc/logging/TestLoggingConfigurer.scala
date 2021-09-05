package org.dauch.kc.logging

import java.util.logging._
import scala.util.chaining.scalaUtilChainingOps

class TestLoggingConfigurer {
  Logger.getLogger("").tap { rootLogger =>
    rootLogger.getHandlers.foreach(rootLogger.removeHandler)
    rootLogger.setLevel(Level.INFO)
    rootLogger.addHandler(TestLoggingHandler)
  }
}
