package org.ku.kc.kafka;

import java.util.TreeMap;

public class KafkaProperties {

  private final TreeMap<String, String> map = new TreeMap<>();

  public TreeMap<String, Object> getMap() {
    return new TreeMap<>(map);
  }

  public void put(String key, String value) {
    map.put(key, value);
  }

  public void putAll(KafkaProperties properties) {
    map.putAll(properties.map);
  }
}
