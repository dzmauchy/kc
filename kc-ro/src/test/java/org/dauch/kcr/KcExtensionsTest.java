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
package org.dauch.kcr;

import org.apache.avro.util.Utf8;
import org.dauch.kcr.groovy.GroovyShellProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("normal")
class KcExtensionsTest {

  @Test
  void testUtf8Equals() {
    var random = new Random(0L);
    var bytes = new byte[16384];
    random.nextBytes(bytes);
    var expectedHex = IntStream.range(0, bytes.length)
      .mapToObj(i -> String.format("%02X", bytes[i]))
      .collect(Collectors.joining());
    var shell = GroovyShellProvider.defaultShell();
    shell.setProperty("v1", new Utf8("abc"));
    shell.setProperty("v2", "abc");
    shell.setProperty("v3", bytes);

    assertEquals(expectedHex, shell.evaluate("v3.hex"));
    assertEquals(true, shell.evaluate("v1 == v2"));
    assertEquals(true, shell.evaluate("v2 == v1"));
    assertEquals(false, shell.evaluate("v1 == 'abcd'"));
    assertEquals(true, shell.evaluate("v1 == 'abc'"));
  }
}
