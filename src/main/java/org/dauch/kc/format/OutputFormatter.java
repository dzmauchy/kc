package org.dauch.kc.format;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import org.apache.avro.generic.GenericRecord;

import java.util.Collection;
import java.util.Map;

public class OutputFormatter {

  public String format(Object object) {
    var builder = new StringBuilder();
    format(object, builder);
    return builder.toString();
  }

  private void format(Object object, StringBuilder builder) {
    if (object == null || object instanceof Boolean || object instanceof Number || object instanceof GenericRecord) {
      builder.append(object);
    } else if (object instanceof CharSequence) {
      builder.append('"');
      JsonStringEncoder.getInstance().quoteAsString((CharSequence) object, builder);
      builder.append('"');
    } else if (object instanceof Collection) {
      var c = (Collection<?>) object;
      var it = c.iterator();
      builder.append('[');
      while (it.hasNext()) {
        var e = it.next();
        format(e, builder);
        if (it.hasNext()) {
          builder.append(", ");
        }
      }
      builder.append(']');
    } else if (object instanceof Map) {
      var m = (Map<?, ?>) object;
      var it = m.entrySet().iterator();
      builder.append('{');
      while (it.hasNext()) {
        var e = it.next();
        format(e.getKey(), builder);
        builder.append(": ");
        format(e.getValue(), builder);
        if (it.hasNext()) {
          builder.append(", ");
        }
      }
      builder.append('}');
    } else {
      throw new IllegalArgumentException("Unknown object type: " + object);
    }
  }
}
