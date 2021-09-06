package org.dauch.test.implicits

import org.apache.kafka.common.serialization.{ByteArraySerializer, DoubleSerializer, FloatSerializer, IntegerSerializer, LongSerializer, Serializer, ShortSerializer, StringSerializer}

import java.lang.{Double => JDouble, Float => JFloat, Integer => JInt, Long => JLong, Short => JShort}

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
