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
