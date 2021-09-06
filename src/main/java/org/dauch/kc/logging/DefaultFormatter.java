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
package org.dauch.kc.logging;

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
    printer.format("%s [%d] %s ", record.getLevel().getName(), getThreadId(record), record.getLoggerName());
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

  @SuppressWarnings("deprecation")
  private long getThreadId(LogRecord record) {
    return record.getThreadID();
  }
}
