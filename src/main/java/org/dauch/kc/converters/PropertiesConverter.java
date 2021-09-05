package org.dauch.kc.converters;

import groovyjarjarpicocli.CommandLine;
import org.dauch.kc.kafka.KafkaProperties;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class PropertiesConverter implements CommandLine.ITypeConverter<KafkaProperties> {

  private static final Pattern PATTERN = Pattern.compile(",,");

  @Override
  public KafkaProperties convert(String s) {
    return PATTERN.splitAsStream(s)
      .flatMap(v -> {
        int index = v.indexOf('=');
        if (index < 0) {
          return Stream.empty();
        } else {
          return Stream.of(Map.entry(v.substring(0, index + 1), v.substring(index + 1)));
        }
      })
      .collect(KafkaProperties::new, (p, e) -> p.put(e.getKey(), e.getValue()), KafkaProperties::putAll);
  }
}
