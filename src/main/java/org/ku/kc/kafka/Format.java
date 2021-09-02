package org.ku.kc.kafka;

import groovy.json.JsonSlurper;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.DecoderFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public enum Format {

  HEX {
    @Override
    public Object decode(byte[] data, EnumMap<DecoderKey, Object> properties) {
      return IntStream.range(0, data.length)
        .mapToObj(i -> String.format("%02X", data[i]))
        .collect(Collectors.joining("", "[" + data.length + "] ", ""));
    }
  },
  BYTES {
    @Override
    public Object decode(byte[] data, EnumMap<DecoderKey, Object> properties) {
      return data;
    }
  },
  BASE64 {
    @Override
    public Object decode(byte[] data, EnumMap<DecoderKey, Object> properties) {
      return data == null ? "" : "[" + data.length + "] " + Base64.getEncoder().encodeToString(data);
    }
  },
  STRING {
    @Override
    public String decode(byte[] data, EnumMap<DecoderKey, Object> properties) {
      return data == null ? null : new String(data, StandardCharsets.UTF_8);
    }
  },
  JSON {
    @Override
    public Object decode(byte[] data, EnumMap<DecoderKey, Object> properties) {
      return data == null ? null : new JsonSlurper().parse(data, "UTF-8");
    }
  },
  LONG {
    @Override
    public Long decode(byte[] data, EnumMap<DecoderKey, Object> properties) {
      return data == null ? null : ByteBuffer.wrap(data).getLong();
    }
  },
  INT {
    @Override
    public Integer decode(byte[] data, EnumMap<DecoderKey, Object> properties) {
      return data == null ? null : ByteBuffer.wrap(data).getInt();
    }
  },
  FLOAT {
    @Override
    public Object decode(byte[] data, EnumMap<DecoderKey, Object> properties) {
      return data == null ? null : ByteBuffer.wrap(data).getFloat();
    }
  },
  DOUBLE {
    @Override
    public Object decode(byte[] data, EnumMap<DecoderKey, Object> properties) {
      return data == null ? null : ByteBuffer.wrap(data).getDouble();
    }
  },
  AVRO {

    private final ConcurrentHashMap<Integer, Schema> schemas = new ConcurrentHashMap<>();

    @Override
    public Object decode(byte[] data, EnumMap<DecoderKey, Object> properties) {
      if (data == null) {
        return null;
      } else {
        var schema = (Schema) properties.get(DecoderKey.SCHEMA);
        if (schema == null) {
          int schemaId = ByteBuffer.wrap(data, 1, 4).getInt();
          schema = schemas.computeIfAbsent(schemaId, id -> {
            try {
              var uri = (URI) properties.get(DecoderKey.SCHEMA_REGISTRY);
              var url = uri.resolve("schemas/ids/" + id).toURL();
              try (var is = url.openStream()) {
                var json = (Map<?, ?>) new JsonSlurper().parse(is, "UTF-8");
                var schemaText = json.get("schema").toString();
                return new Schema.Parser().parse(schemaText);
              }
            } catch (IOException e) {
              throw new UncheckedIOException("Unable to decode schema for id = " + id, e);
            }
          });
        }
        var reader = new GenericDatumReader<>(schema);
        try {
          return reader.read(null, DecoderFactory.get().binaryDecoder(data, 5, data.length - 5, null));
        } catch (IOException e) {
          throw new UncheckedIOException("Unable to decode data " + Base64.getEncoder().encodeToString(data) + " with " + schema, e);
        }
      }
    }
  };

  public abstract Object decode(byte[] data, EnumMap<DecoderKey, Object> properties);
}
