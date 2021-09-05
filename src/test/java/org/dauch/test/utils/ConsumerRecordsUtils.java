package org.dauch.test.utils;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public interface ConsumerRecordsUtils {

  static void write(OutputStream os, ConsumerRecords<byte[], byte[]> records) throws IOException {
    if (records.isEmpty()) {
      return;
    }
    var bos = new ByteArrayOutputStream();
    var dos = new DataOutputStream(bos);
    dos.writeInt(records.count());
    for (var r : records) {
      dos.writeUTF(r.topic());
      dos.writeInt(r.partition());
      dos.writeLong(r.offset());
      dos.writeLong(r.timestamp());
      dos.writeInt(r.timestampType().ordinal());
      dos.writeInt(r.leaderEpoch().orElse(-1));
      dos.writeInt(r.key() == null ? -1 : r.key().length);
      dos.writeInt(r.value() == null ? -1 : r.value().length);
      var headers = r.headers().toArray();
      dos.writeInt(headers.length);
      for (var h : headers) {
        dos.writeUTF(h.key());
        dos.writeInt(h.value() == null ? -1 : h.value().length);
      }
    }
    bos.writeTo(os);
    os.flush();
  }

  static ConsumerRecords<byte[], byte[]> read(InputStream is) throws IOException {
    var dis = new DataInputStream(is);
    int size;
    try {
      size = dis.readInt();
    } catch (EOFException e) {
      return ConsumerRecords.empty();
    }
    var map = new HashMap<TopicPartition, List<ConsumerRecord<byte[], byte[]>>>();
    var timestampTypes = TimestampType.values();
    for (int i = 0; i < size; i++) {
      var topic = dis.readUTF();
      var partition = dis.readInt();
      var offset = dis.readLong();
      var timestamp = dis.readLong();
      var timestampType = timestampTypes[dis.readInt()];
      var leaderEpoch = Optional.of(dis.readInt()).filter(v -> v >= 0);
      var keyLen = dis.readInt();
      var key = keyLen >= 0 ? dis.readNBytes(keyLen) : null;
      var valueLen = dis.readInt();
      var value = valueLen >= 0 ? dis.readNBytes(valueLen) : null;
      var hLen = dis.readInt();
      var hs = new ArrayList<Header>(hLen);
      for (int j = 0; j < hLen; j++) {
        var k = dis.readUTF();
        var vLen = dis.readInt();
        var v = vLen >= 0 ? dis.readNBytes(vLen) : null;
        hs.add(new RecordHeader(k, v));
      }
      var headers = new RecordHeaders(hs);
      map
        .computeIfAbsent(new TopicPartition(topic, partition), tp -> new ArrayList<>())
        .add(new ConsumerRecord<>(
          topic,
          partition,
          offset,
          timestamp,
          timestampType,
          null,
          key == null ? 0 : key.length,
          value == null ? 0 : value.length,
          key,
          value,
          headers,
          leaderEpoch
        ));
    }
    return new ConsumerRecords<>(map);
  }
}
