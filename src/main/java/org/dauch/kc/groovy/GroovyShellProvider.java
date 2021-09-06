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
