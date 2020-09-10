package org.dzmauchy.kc;

import groovyjarjarpicocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command()
public class OffsetsCommand implements Callable<Integer> {
  @Override
  public Integer call() throws Exception {
    return null;
  }
}
