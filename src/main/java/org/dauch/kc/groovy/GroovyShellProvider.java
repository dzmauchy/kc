package org.dauch.kc.groovy;

import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;

public interface GroovyShellProvider {

  GroovyShell newShell();

  static GroovyShell defaultShell() {
    var compilerConfiguration = new CompilerConfiguration();
    compilerConfiguration.setRecompileGroovySource(false);
    compilerConfiguration.setTargetBytecode(CompilerConfiguration.JDK11);
    compilerConfiguration.setParameters(true);
    return new GroovyShell(compilerConfiguration);
  }

}
