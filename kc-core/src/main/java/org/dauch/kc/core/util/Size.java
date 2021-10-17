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

import java.math.BigInteger;

import static java.util.Objects.requireNonNull;

public final class Size {

  private final BigInteger size;

  private Size(BigInteger size) {
    this.size = size;
  }

  public int toInt() {
    try {
      return size.intValueExact();
    } catch (Throwable e) {
      throw new IllegalStateException("Size " + size + " cannot be converted to int");
    }
  }

  public long toLong() {
    try {
      return size.longValueExact();
    } catch (Throwable e) {
      throw new IllegalStateException("Size " + size + " cannot be converted to long");
    }
  }

  public static Size parse(String size) {
    var sz = requireNonNull(size, "size is null").toLowerCase();
    if (sz.isBlank()) {
      throw new IllegalArgumentException("size is blank");
    }
    var lastChar = sz.charAt(sz.length() - 1);
    switch (lastChar) {
      case 'g':
        return parse(sz.substring(0, sz.length() - 1), 1024L * 1024L * 1024L);
      case 'm':
        return parse(sz.substring(0, sz.length() - 1), 1024L * 1024L);
      case 'k':
        return parse(sz.substring(0, sz.length() - 1), 1024L);
      default:
        try {
          return new Size(new BigInteger(sz));
        } catch (Throwable e) {
          throw new IllegalArgumentException("Invalid size: " + sz);
        }
    }
  }

  private static Size parse(String numberPart, long suffix) {
    try {
      var num = new BigInteger(numberPart);
      return new Size(num.multiply(BigInteger.valueOf(suffix)));
    } catch (Throwable e) {
      throw new IllegalArgumentException("Invalid number part of size: " + numberPart);
    }
  }
}
