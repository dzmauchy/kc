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
import groovyjarjarpicocli.CommandLine.Command;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.dauch.kcr.util.ExceptionUtils.exceptionToMap;

@Command(
  name = "check",
  aliases = {"q"},
  description = "Check json input",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
)
public class CheckCommand extends AbstractEvalCommand implements Callable<Integer> {

  @Override
  protected Object transform(Object value) {
    if (value == null) {
      return true;
    } else if (value instanceof Boolean) {
      return value;
    } else {
      return "Invalid result: " + value;
    }
  }

  @Override
  public Integer call() throws Exception {
    var slurper = new JsonSlurper().setType(JsonParserType.CHARACTER_SOURCE);
    var json = slurper.parse(System.in, "UTF-8");
    shell.setVariable("result", json);
    shell.setVariable("$r", json);
    var result = result().parallelStream()
      .map(f -> {
        try {
          return f.get();
        } catch (ExecutionException e) {
          return e.getCause() instanceof AssertionError
            ? e.getCause().getMessage()
            : exceptionToMap(e.getCause());
        } catch (Throwable e) {
          return exceptionToMap(e);
        }
      })
      .collect(Collectors.toList());
    out.println(finalOutput(JsonOutput.toJson(result)));
    if (result.parallelStream().allMatch(b -> b instanceof Boolean && (boolean) b)) {
      return 0;
    } else if (result.parallelStream().anyMatch(e -> e instanceof Map)) {
      return 2;
    } else if (result.parallelStream().anyMatch(e -> e instanceof String)) {
      return 3;
    } else {
      return 1;
    }
  }
}
