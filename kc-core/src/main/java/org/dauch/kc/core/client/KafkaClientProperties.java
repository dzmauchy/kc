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
package org.dauch.kc.core.client;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketOption;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;
import static java.util.Objects.requireNonNull;
import static org.dauch.kc.core.util.Validation.validateArgument;

public final class KafkaClientProperties {

  private final LinkedList<InetSocketAddress> bootstrapServers = new LinkedList<>();
  private final HashMap<SocketOption<?>, Object> socketOptions = new HashMap<>();
  private SSLContext sslContext;
  private String bufferConfig;
  private int timeout = 60_000;

  private void addBootstrapServer(String server) {
    var addr = requireNonNull(server, "server is null").trim();
    var index = addr.indexOf(':');
    if (index < 0) {
      bootstrapServers.add(new InetSocketAddress(server, 9092));
    } else {
      var host = addr.substring(0, index);
      var port = validateArgument(
        () -> parseInt(addr.substring(index + 1)),
        () -> "Invalid port: " + addr
      );
      bootstrapServers.add(new InetSocketAddress(host, port));
    }
  }

  public KafkaClientProperties withBootstrapServers(Iterable<String> servers) {
    servers.forEach(this::addBootstrapServer);
    return this;
  }

  public KafkaClientProperties withBootstrapServers(String... servers) {
    for (var s : servers) addBootstrapServer(s);
    return this;
  }

  public KafkaClientProperties withSslContext(SSLContext context) {
    this.sslContext = context;
    return this;
  }

  public KafkaClientProperties withTimeout(int timeout) {
    this.timeout = timeout;
    return this;
  }

  public <O> KafkaClientProperties withSocketOption(SocketOption<O> option, O value) {
    socketOptions.put(option, value);
    return this;
  }

  public KafkaClientProperties withBufferConfig(String config) {
    this.bufferConfig = config;
    return this;
  }

  public Stream<InetSocketAddress> getBootstrapServers() {
    return bootstrapServers.stream();
  }

  public SSLContext getSslContext() {
    return sslContext;
  }

  public int getTimeout() {
    return timeout;
  }

  public String getBufferConfig() {
    return bufferConfig;
  }

  @SuppressWarnings("unchecked")
  public void configure(Socket socket) throws IOException {
    for (var e : this.socketOptions.entrySet()) {
      var opt = e.getKey();
      var v = e.getValue();
      socket.setOption((SocketOption<Object>) opt, v);
    }
  }
}
