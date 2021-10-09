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
package org.dauch.kcr.util;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public interface ExceptionUtils {

  static LinkedHashMap<String, Object> exceptionToMap(Throwable throwable) {
    if (throwable instanceof ExecutionException) {
      throwable = throwable.getCause();
    }
    var map = new LinkedHashMap<String, Object>();
    {
      var msg = throwable.getMessage();
      if (msg != null) {
        map.put("message", msg);
      }
    }
    {
      var cause = throwable.getCause();
      if (cause != null) {
        map.put("cause", exceptionToMap(cause));
      }
    }
    {
      var suppressed = throwable.getSuppressed();
      if (suppressed.length > 0) {
        var list = Arrays.stream(suppressed).parallel()
          .map(ExceptionUtils::exceptionToMap)
          .collect(Collectors.toList());
        map.put("suppressed", list);
      }
    }
    return map;
  }
}
