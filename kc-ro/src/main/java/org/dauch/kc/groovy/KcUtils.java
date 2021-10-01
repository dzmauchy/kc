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

public class KcUtils {

  static byte hexSymbol(int s) {
    switch (s) {
      case 0x0: return '0';
      case 0x1: return '1';
      case 0x2: return '2';
      case 0x3: return '3';
      case 0x4: return '4';
      case 0x5: return '5';
      case 0x6: return '6';
      case 0x7: return '7';
      case 0x8: return '8';
      case 0x9: return '9';
      case 0xA: return 'A';
      case 0xB: return 'B';
      case 0xC: return 'C';
      case 0xD: return 'D';
      case 0xE: return 'E';
      case 0xF: return 'F';
      default: throw new IllegalArgumentException(Integer.toString(s));
    }
  }
}
