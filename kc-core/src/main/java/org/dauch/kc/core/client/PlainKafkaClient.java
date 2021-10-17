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

import org.dauch.kc.core.buffer.BufferPool;

import java.io.IOException;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public final class PlainKafkaClient implements KafkaClient {

  private final String clientId;
  private final SocketPool socketPool;
  private final BufferPool bufferPool;

  public PlainKafkaClient(String clientId, KafkaClientProperties properties) {
    this.clientId = requireNonNull(clientId, "clientId is null");
    if (clientId.isBlank()) {
      throw new IllegalArgumentException("clientId is blank");
    }
    this.socketPool = new SocketPool(requireNonNull(properties, "properties is null"));
    this.bufferPool = new BufferPool(clientId, properties);
  }

  @Override
  public Stream<String> listTopics(long timeoutMillis) {
    return null;
  }

  @Override
  public String getClientId() {
    return clientId;
  }

  @Override
  public void close() throws IOException {
    socketPool.close();
  }
}
