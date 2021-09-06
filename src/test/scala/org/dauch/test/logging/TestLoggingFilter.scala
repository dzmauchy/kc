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
package org.dauch.test.logging

import java.io.{EOFException, IOException}
import java.net.{ConnectException, SocketException}
import java.util.logging.{Filter, LogRecord}

object TestLoggingFilter extends Filter {
  override def isLoggable(r: LogRecord): Boolean = {
    r.getLoggerName match {
      case "org.apache.zookeeper.server.quorum.LearnerHandler" =>
        r.getMessage match {
          case "Ignoring unexpected exception" => false
          case "Unexpected exception in LearnerHandler." if r.getThrown.isInstanceOf[InterruptedException] => false
          case "Unexpected exception causing shutdown while sock still open" if r.getThrown.isInstanceOf[EOFException] => false
          case _ => true
        }
      case "org.apache.zookeeper.server.quorum.Learner" =>
        r.getMessage.trim match {
          case "Exception when following the leader" =>
            r.getThrown match {
              case _: EOFException => false
              case e: IOException if e.getMessage.startsWith("Failed") => false
              case e: SocketException if e.getMessage.contains("closed") => false
              case _ => true
            }
          case "Exception while shutting down acceptor." =>
            r.getThrown match {
              case e: SocketException if e.getMessage == "Socket closed" => false
              case _ => true
            }
          case "Interrupted while trying to connect to Leader" =>
            r.getThrown match {
              case _: InterruptedException => false
              case _ => true
            }
          case _ => true
        }
      case "org.apache.zookeeper.server.quorum.QuorumPeer" =>
        r.getMessage match {
          case "Unexpected exception" if r.getThrown.isInstanceOf[InterruptedException] => false
          case _ => true
        }
      case "org.apache.zookeeper.server.quorum.Leader" =>
        r.getMessage match {
          case "Exception while shutting down acceptor." =>
            r.getThrown match {
              case e: SocketException if e.getMessage == "Socket closed" => false
              case _ => true
            }
          case "Exception while accepting follower" =>
            r.getThrown match {
              case e: SocketException if e.getMessage.contains("closed") => false
              case _ => true
            }
          case _ => true
        }
      case "org.apache.zookeeper.server.quorum.QuorumCnxManager" =>
        r.getMessage match {
          case "Interrupted while waiting for message on queue" =>
            r.getThrown match {
              case _: InterruptedException => false
              case _ => true
            }
          case s"Connection broken for id$_" =>
            r.getThrown match {
              case _: EOFException => false
              case e: SocketException if e.getMessage == "Socket closed" => false
              case _ => true
            }
          case s"Cannot open channel to$_" =>
            r.getThrown match {
              case e: ConnectException if e.getMessage == "Connection refused" => false
              case _ => true
            }
          case s"Exception when using channel$_" =>
            r.getThrown match {
              case e: SocketException if e.getMessage == "Broken pipe" => false
              case _ => true
            }
          case _ => true
        }
      case "org.apache.zookeeper.server.ZooKeeperCriticalThread" =>
        r.getThrown match {
          case e: SocketException if e.getMessage.contains("closed") => false
          case _ => true
        }
      case "org.apache.zookeeper.server.NIOServerCnxn" =>
        r.getMessage match {
          case s"Close of session$_" =>
            r.getThrown match {
              case e: IOException if e.getMessage == "ZooKeeperServer not running" => false
              case _ => true
            }
          case _ => true
        }
      case "org.apache.zookeeper.ClientCnxn" =>
        r.getMessage match {
          case s"$_ Closing socket connection.$_" => false
          case s"An exception was thrown while closing send thread for session$_" => false
          case _ => true
        }
      case _ => true
    }
  }
}
