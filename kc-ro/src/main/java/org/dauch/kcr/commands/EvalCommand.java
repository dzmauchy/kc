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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.dauch.kcr.util.ExceptionUtils.exceptionToMap;

@Command(
  name = "eval",
  aliases = {"e"},
  description = "Evaluate",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
)
public class EvalCommand extends AbstractEvalCommand implements Callable<Integer> {

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
          return exceptionToMap(e.getCause());
        } catch (Throwable e) {
          return exceptionToMap(e);
        }
      })
      .collect(Collectors.toList());
    switch (result.size()) {
      case 0:
        if (verbose) {
          err.println("No scripts");
        }
        break;
      case 1:
        out.println(finalOutput(JsonOutput.toJson(result.get(0))));
        break;
      default:
        out.println(finalOutput(JsonOutput.toJson(result)));
        break;
    }
    return 0;
  }

  @Override
  protected Object transform(Object value) {
    return value;
  }
}
