package org.ku.kc.version;

import groovyjarjarpicocli.CommandLine;

import java.util.Properties;

public class KcVersionProvider implements CommandLine.IVersionProvider {
  @Override
  public String[] getVersion() throws Exception {
    var cl = Thread.currentThread().getContextClassLoader();
    var props = new Properties();
    try (var is = cl.getResourceAsStream("main.properties")) {
      props.load(is);
    }
    return new String[]{
      "Version " + props.getProperty("version"),
      "(c) " + props.getProperty("year") + " Dzmiter Auchynnikau"
    };
  }
}
