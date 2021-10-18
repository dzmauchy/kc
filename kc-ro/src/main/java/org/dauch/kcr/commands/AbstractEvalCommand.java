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
package org.dauch.kcr.commands;

import groovy.json.JsonOutput;
import groovy.lang.GroovyShell;
import groovyjarjarpicocli.CommandLine.Option;
import groovyjarjarpicocli.CommandLine.Parameters;
import org.dauch.kcr.groovy.GroovyShellProvider;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newBufferedReader;
import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static java.util.stream.Collectors.toUnmodifiableList;

public abstract class AbstractEvalCommand extends AbstractCommand {

  protected final GroovyShell shell = GroovyShellProvider.defaultShell();

  @Option(
    names = {"-p", "--pretty"},
    description = "Pretty print",
    fallbackValue = "true",
    defaultValue = "false"
  )
  public boolean pretty;

  @Parameters(
    description = "Check scripts"
  )
  public List<String> scripts = emptyList();

  protected abstract Object transform(Object value);

  protected String finalOutput(String outputText) {
    return pretty ? JsonOutput.prettyPrint(outputText) : outputText;
  }

  protected List<CompletableFuture<Object>> result() {
    return IntStream.range(0, scripts.size()).parallel()
      .mapToObj(i -> {
        var script = scripts.get(i);
        final String fileName;
        if (script.startsWith("@")) {
          fileName = script.substring(1) + (script.endsWith(".groovy") ? "" : ".groovy");
        } else {
          fileName = "script_" + i + ".groovy";
        }
        try {
          if (script.startsWith("@")) {
            try (var reader = newBufferedReader(Path.of(fileName), UTF_8)) {
              return completedFuture(transform(shell.evaluate(reader, fileName)));
            }
          } else {
            return completedFuture(transform(shell.evaluate(script, fileName)));
          }
        } catch (Throwable e) {
          return failedFuture(e);
        }
      })
      .collect(toUnmodifiableList());
  }
}
