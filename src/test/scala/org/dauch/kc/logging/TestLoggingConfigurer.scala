package org.dauch.kc.logging

import java.util.logging._

class TestLoggingConfigurer {
  LogManager.getLogManager.reset()
  Logger.getLogger("").setLevel(Level.INFO)
  Logger.getLogger("").addHandler(TestLoggingHandler)
}
