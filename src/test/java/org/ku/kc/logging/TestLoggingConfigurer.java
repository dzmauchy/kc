package org.ku.kc.logging;

import java.util.logging.*;

public class TestLoggingConfigurer {

  public TestLoggingConfigurer() {
    LogManager.getLogManager().reset();
    Logger.getLogger("").setLevel(Level.INFO);
    Logger.getLogger("").addHandler(new Handler() {
      @Override
      public void publish(LogRecord record) {
        System.out.println(new DefaultFormatter().format(record));
      }

      @Override
      public void flush() {
      }

      @Override
      public void close() throws SecurityException {
      }
    });
  }
}
