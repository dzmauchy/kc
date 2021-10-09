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
package org.dauch.kcr.converters;

import groovyjarjarpicocli.CommandLine;
import org.dauch.kcr.kafka.KafkaProperties;

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
