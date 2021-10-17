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
package org.dauch.kc.core.util;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

public interface Validation {

  static <T> T validateArgument(Callable<T> callable, Supplier<String> error) {
    try {
      return callable.call();
    } catch (Exception e) {
      throw new IllegalArgumentException(error.get(), e);
    }
  }

  static <T> T validateState(Callable<T> callable, Supplier<String> error) {
    try {
      return callable.call();
    } catch (Exception e) {
      throw new IllegalStateException(error.get(), e);
    }
  }
}
