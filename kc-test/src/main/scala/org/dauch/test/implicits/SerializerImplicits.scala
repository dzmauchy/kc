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

import org.apache.kafka.common.serialization.*

import java.lang.{Double as JDouble, Float as JFloat, Integer as JInt, Long as JLong, Short as JShort}

trait SerializerImplicits {
  implicit def longSerializer: Serializer[JLong] = new LongSerializer
  implicit def intSerializer: Serializer[JInt] = new IntegerSerializer
  implicit def bytesSerializer: Serializer[Array[Byte]] = new ByteArraySerializer
  implicit def stringSerializer: Serializer[String] = new StringSerializer
  implicit def floatSerializer: Serializer[JFloat] = new FloatSerializer
  implicit def doubleSerializer: Serializer[JDouble] = new DoubleSerializer
  implicit def shortSerializer: Serializer[JShort] = new ShortSerializer
}

object SerializerImplicits extends SerializerImplicits
