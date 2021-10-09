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
package org.dauch.kcr.groovy;

import org.apache.avro.util.Utf8;

import java.nio.charset.StandardCharsets;

interface KcExtensions {

  static int compareTo(Utf8 utf8, String string) {
    var s = utf8.toString();
    return s.compareTo(string);
  }

  static int compareTo(String string, Utf8 utf8) {
    return -compareTo(utf8, string);
  }

  static String getHex(byte[] data) {
    var bytes = new byte[data.length * 2];
    var len = data.length;
    for (int i = 0; i < len; i++) {
      var b = Byte.toUnsignedInt(data[i]);
      var h = b / 16;
      var l = b % 16;
      var base = i * 2;
      bytes[base] = KcUtils.hexSymbol(h);
      bytes[base + 1] = KcUtils.hexSymbol(l);
    }
    return new String(bytes, StandardCharsets.ISO_8859_1);
  }
}
