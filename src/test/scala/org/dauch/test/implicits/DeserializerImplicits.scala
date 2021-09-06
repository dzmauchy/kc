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
package org.dauch.test.implicits

import org.apache.kafka.common.serialization._

import java.lang.{Double => JDouble, Float => JFloat, Integer => JInt, Long => JLong, Short => JShort}

trait DeserializerImplicits {
  implicit def longDeserializer: Deserializer[JLong] = new LongDeserializer
  implicit def intDeserializer: Deserializer[JInt] = new IntegerDeserializer
  implicit def bytesDeserializer: Deserializer[Array[Byte]] = new ByteArrayDeserializer
  implicit def stringDeserializer: Deserializer[String] = new StringDeserializer
  implicit def floatDeserializer: Deserializer[JFloat] = new FloatDeserializer
  implicit def doubleDeserializer: Deserializer[JDouble] = new DoubleDeserializer
  implicit def shortDeserializer: Deserializer[JShort] = new ShortDeserializer
}

object DeserializerImplicits extends DeserializerImplicits
