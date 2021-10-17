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

import org.dauch.kc.core.util.IOSupplier;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.Objects.requireNonNull;
import static java.util.stream.IntStream.range;

final class SocketPool implements Closeable {

  private final KafkaClientProperties props;
  private final InetSocketAddress[] addresses;
  private final Socket[] sockets;
  private final DataInputStream[] inputStreams;
  private final DataOutputStream[] outputStreams;
  private final Object[] locks;

  SocketPool(KafkaClientProperties props) {
    this.props = requireNonNull(props, "props is null");
    this.addresses = props.getBootstrapServers().toArray(InetSocketAddress[]::new);
    this.sockets = new Socket[this.addresses.length];
    this.inputStreams = new DataInputStream[sockets.length];
    this.outputStreams = new DataOutputStream[sockets.length];
    this.locks = range(0, sockets.length).mapToObj(i -> new Object()).toArray();
  }

  <R> R call(IOSupplier<R> supplier) throws IOException {
    var idx = ThreadLocalRandom.current().nextInt(sockets.length);
    try {
      return call(idx, supplier);
    } catch (Throwable e) {
      for (int i = 0; i < sockets.length; i++) {
        if (idx != i) {
          try {
            return call(i, supplier);
          } catch (Throwable x) {
            e.addSuppressed(x);
          }
        }
      }
      throw e;
    }
  }

  private <R> R call(int idx, IOSupplier<R> supplier) throws IOException {
    synchronized (locks[idx]) {
      if (sockets[idx] == null) {
        try {
          var s = sockets[idx] = obtainSocket(addresses[idx]);
          var is = inputStreams[idx] = new DataInputStream(s.getInputStream());
          var os = outputStreams[idx] = new DataOutputStream(s.getOutputStream());
          return supplier.get(s, is, os);
        } catch (IOException e) {
          try {
            close(idx);
          } catch (Throwable x) {
            e.addSuppressed(x);
          }
          throw e;
        }
      } else {
        return supplier.get(sockets[idx], inputStreams[idx], outputStreams[idx]);
      }
    }
  }

  private void close(int idx) throws IOException {
    Throwable exception = null;
    if (outputStreams[idx] != null) {
      try {
        outputStreams[idx].close();
      } catch (Throwable x) {
        exception = x;
      } finally {
        outputStreams[idx] = null;
      }
    }
    if (inputStreams[idx] != null) {
      try {
        inputStreams[idx].close();
      } catch (Throwable x) {
        if (exception == null) exception = x;
        else exception.addSuppressed(x);
      } finally {
        inputStreams[idx] = null;
      }
    }
    if (sockets[idx] != null) {
      try {
        sockets[idx].close();
      } catch (Throwable x) {
        if (exception == null) exception = x;
        else exception.addSuppressed(x);
      } finally {
        sockets[idx] = null;
      }
    }
    if (exception != null) {
      try {
        throw exception;
      } catch (IOException | RuntimeException | Error e) {
        throw e;
      } catch (Throwable e) {
        throw new IOException(e);
      }
    }
  }

  private Socket obtainSocket(InetSocketAddress address) throws IOException {
    var context = props.getSslContext();
    var socket = context == null ? new Socket() : context.getSocketFactory().createSocket();
    props.configure(socket);
    socket.setSoTimeout(props.getTimeout());
    socket.connect(address, props.getTimeout());
    return socket;
  }

  @Override
  public void close() throws IOException {
    IOException exception = null;
    for (int i = 0; i < sockets.length; i++) {
      try {
        close(i);
      } catch (IOException e) {
        if (exception == null) exception = e;
        else exception.addSuppressed(e);
      } catch (Throwable e) {
        if (exception == null) exception = new IOException(e);
        else exception.addSuppressed(e);
      }
    }
    if (exception != null) {
      throw exception;
    }
  }
}
