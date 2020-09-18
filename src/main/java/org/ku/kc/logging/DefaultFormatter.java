package org.ku.kc.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.time.ZoneId;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class DefaultFormatter extends Formatter {
  @Override
  public String format(LogRecord record) {
    var time = record.getInstant().atZone(ZoneId.systemDefault()).toLocalTime();
    var writer = new StringWriter();
    var buffer = writer.getBuffer();
    var printer = new PrintWriter(writer);
    printer.format("%02d:%02d:%02d.%03d ", time.getHour(), time.getMinute(), time.getSecond(), time.getNano() / 1_000_000);
    printer.format("[%d] %s ", record.getThreadID(), record.getLoggerName());
    if (record.getParameters() != null && record.getParameters().length > 0) {
      try {
        new MessageFormat(record.getMessage()).format(record.getParameters(), buffer, null);
      } catch (RuntimeException e) {
        buffer.append(record.getMessage());
      }
    } else {
      buffer.append(record.getMessage());
    }
    if (record.getThrown() != null) {
      printer.println();
      record.getThrown().printStackTrace(printer);
    }
    return buffer.toString();
  }
}
