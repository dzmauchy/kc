package org.dzmauchy.kc.version;

import groovyjarjarpicocli.CommandLine;

public class KcVersionProvider implements CommandLine.IVersionProvider {
  @Override
  public String[] getVersion() throws Exception {
    return new String[]{"1.0"};
  }
}
