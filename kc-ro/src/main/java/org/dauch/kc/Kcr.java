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
package org.dauch.kc;

import groovyjarjarpicocli.CommandLine;
import org.dauch.kc.commands.MainCommand;
import org.dauch.kc.logging.DefaultFormatter;
import org.dauch.kc.version.KcVersionProvider;

import java.util.logging.FileHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Kcr {

  public static void main(String... args) throws Exception {
    initLogging();
    var commandLine = new CommandLine(new MainCommand());
    commandLine.getCommandSpec().versionProvider(new KcVersionProvider());
    int code = commandLine.execute(args);
    System.exit(code);
  }

  private static void initLogging() throws Exception {
    LogManager.getLogManager().reset();
    var handler = new FileHandler("%t/kc-%g-%u.log", 0L, 1, false);
    handler.setFormatter(new DefaultFormatter());
    Logger.getLogger("").addHandler(handler);
  }
}
