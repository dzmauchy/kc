package org.dauch.kc.logging

import java.util.logging.LogManager

final class TestLogManager extends LogManager {

  super.reset()

  override def reset(): Unit = {
  }
}
