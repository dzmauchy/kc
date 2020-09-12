package org.dzmauchy.kc.kafka;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.TreeMap;

public class KafkaProperties {

  private final TreeMap<String, String> map = new TreeMap<>();

  public NavigableMap<String, String> getMap() {
    return Collections.unmodifiableNavigableMap(map);
  }

  public void put(String key, String value) {
    map.put(key, value);
  }

  public void putAll(KafkaProperties properties) {
    map.putAll(properties.map);
  }
}
