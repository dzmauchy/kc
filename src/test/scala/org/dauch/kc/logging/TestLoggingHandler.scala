package org.dauch.kc.logging

import java.util.logging.{Handler, LogRecord}

object TestLoggingHandler extends Handler {

  setFilter(TestLoggingFilter)
  setFormatter(new DefaultFormatter)

  override def publish(record: LogRecord): Unit = {
    if (isLoggable(record)) {
      println(getFormatter.format(record))
    }
  }

  override def flush(): Unit = ()
  override def close(): Unit = ()
}
