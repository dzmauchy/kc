package org.dzmauchy.kc.commands;

import groovyjarjarpicocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(
  name = "offsets",
  aliases = {"o"}
)
public class OffsetsCommand implements Callable<Integer> {
  @Override
  public Integer call() throws Exception {
    return null;
  }
}
