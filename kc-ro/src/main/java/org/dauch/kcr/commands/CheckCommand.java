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
import groovy.json.JsonParserType;
import groovy.json.JsonSlurper;
import groovy.lang.GroovyShell;
import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Parameters;
import org.dauch.kcr.groovy.GroovyShellProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Map.entry;
import static java.util.stream.Collectors.toConcurrentMap;
import static org.dauch.kcr.util.ExceptionUtils.exceptionToMap;

@Command(
  name = "check",
  aliases = {"q"},
  description = "Check json input",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
)
public class CheckCommand extends AbstractCommand implements Callable<Integer> {

  private final GroovyShell shell = GroovyShellProvider.defaultShell();

  @Parameters(
    description = "Check scripts"
  )
  public List<String> scripts = emptyList();

  @Override
  public Integer call() throws Exception {
    var slurper = new JsonSlurper().setType(JsonParserType.CHARACTER_SOURCE);
    var json = slurper.parse(System.in, "UTF-8");
    shell.setVariable("result", json);
    shell.setVariable("$r", json);
    var result = IntStream.range(0, scripts.size()).parallel()
      .mapToObj(i -> {
        var script = scripts.get(i);
        final String fileName;
        if (script.startsWith("@")) {
          fileName = script.substring(1) + (script.endsWith(".groovy") ? "" : ".groovy");
        } else {
          fileName = "script_" + i + ".groovy";
        }
        try {
          final Object r;
          if (script.startsWith("@")) {
            try (var reader = Files.newBufferedReader(Path.of(fileName), UTF_8)) {
              r = shell.evaluate(reader, fileName);
            }
          } else {
            r = shell.evaluate(script, fileName);
          }
          if (r == null) {
            return entry(i, true);
          } else if (r instanceof Boolean) {
            return entry(i, (boolean) r);
          } else {
            throw new IllegalStateException(r.toString());
          }
        } catch (AssertionError e) {
          report(e);
          return entry(i, e.getMessage());
        } catch (Throwable e) {
          report(e);
          return entry(i, exceptionToMap(e));
        }
      })
      .collect(toConcurrentMap(Entry::getKey, Entry::getValue, (e1, e2) -> e2, ConcurrentSkipListMap::new));
    out.println(JsonOutput.prettyPrint(JsonOutput.toJson(result)));
    if (result.values().parallelStream().allMatch(b -> b instanceof Boolean && (boolean) b)) {
      return 0;
    } else if (result.values().parallelStream().anyMatch(e -> e instanceof Map)) {
      return 2;
    } else if (result.values().parallelStream().anyMatch(e -> e instanceof String)) {
      return 3;
    } else {
      return 1;
    }
  }
}
