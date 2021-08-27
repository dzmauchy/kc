package org.ku.kc.manual;

import org.ku.kc.Kc;

public class KcTest {
  public static void main(String... args) throws Exception {
    System.setProperty("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
    System.setProperty("SCHEMA_REGISTRY", "http://localhost/");
    Kc.main(args);
  }
}
