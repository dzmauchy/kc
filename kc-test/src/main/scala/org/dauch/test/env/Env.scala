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
package org.dauch.test.env

import com.typesafe.scalalogging.StrictLogging
import org.dauch.test.env.Env.{Closer, EventuallyTimeout}

import java.io.File
import java.lang.ref.Cleaner
import java.nio.file.{Files, NotDirectoryException, Path}
import java.util.concurrent.{ConcurrentLinkedDeque, ExecutorService, TimeUnit}
import scala.concurrent.TimeoutException
import scala.reflect.io.Directory
import scala.util.Using
import scala.util.Using.Releasable
import scala.util.control.NonFatal

trait Env extends StrictLogging {

  def beforeAll(): Unit = ()
  def afterAll(): Unit = ()
  def beforeEach(): Unit = ()
  def afterEach(): Unit = ()

  def eventually[R](code: => R)(implicit timeout: EventuallyTimeout = EventuallyTimeout(60_000L)): R = {
    val startTime = System.nanoTime()
    while (true) {
      try {
        return code
      } catch {
        case NonFatal(e) =>
          if (System.nanoTime() - startTime > timeout.timeout * 1_000_000L) {
            throw e
          } else {
            Thread.sleep(100L)
          }
      }
    }
    throw new IllegalStateException()
  }

  def release[R](f: Closer => R): R = {
    val rss = new ConcurrentLinkedDeque[(AnyRef, Releasable[AnyRef])]()
    val closer = new Closer {
      override def apply[T <: AnyRef : Releasable](f: => T): T = {
        val r = f
        if (r != null) {
          rss.add(r -> implicitly[Releasable[T]].asInstanceOf[Releasable[AnyRef]])
        }
        r
      }
      override def close[T <: AnyRef : Releasable](ref: T): Unit = {
        rss.removeIf {case (r, rs) =>
          if (r eq ref) {
            rs.release(r)
            true
          } else {
            false
          }
        }
      }
    }
    val result = try {
      f(closer)
    } catch {
      case e: Throwable =>
        val it = rss.descendingIterator()
        while (it.hasNext) {
          val (r, rs) = it.next()
          try {
            rs.release(r)
          } catch {
            case x: Throwable => e.addSuppressed(x)
          } finally {
            it.remove()
          }
        }
        throw e
    }
    var exception: Throwable = null
    val it = rss.descendingIterator()
    while (it.hasNext) {
      val (r, rs) = it.next()
      try {
        rs.release(r)
      } catch {
        case x: Throwable => if (exception == null) exception = x else exception.addSuppressed(x)
      } finally {
        it.remove()
      }
    }
    if (exception != null) {
      throw exception
    } else {
      result
    }
  }

  implicit def fileReleasable: Releasable[File] = f => {
    if (f != null) {
      new Directory(f).deleteRecursively()
    }
  }

  implicit def pathReleasable: Releasable[Path] = f => {
    if (f != null) {
      def delete(p: Path): Unit = {
        Using(Files.newDirectoryStream(p))(_.forEach(delete))
          .recover { case _: NotDirectoryException => Files.deleteIfExists(p) }
          .get
      }
      delete(f)
    }
  }

  implicit def autoCloseableReleasable: Releasable[AutoCloseable] = a => {
    if (a != null) {
      a.close()
    }
  }

  implicit def threadReleasable: Releasable[Thread] = t => {
    if (t != null) {
      try {
        t.join(60_000L)
      } catch {
        case e: Throwable =>
          t.interrupt()
          try {
            t.join(60_000L)
            if (t.isAlive) {
              throw new TimeoutException
            }
          } catch {
            case x: Throwable =>
              x.addSuppressed(e)
              throw e
          }
      }
    }
  }

  implicit def executorServiceReleasable: Releasable[ExecutorService] = s => {
    if (s != null) {
      s.shutdown()
      if (!s.awaitTermination(1L, TimeUnit.MINUTES)) {
        s.shutdownNow()
        if (!s.awaitTermination(1L, TimeUnit.MINUTES)) {
          throw new TimeoutException
        }
      }
    }
  }
}

object Env {
  val EnvCleaner: Cleaner = Cleaner.create()
  trait Closer {
    def apply[T <: AnyRef : Releasable](f: => T): T
    def register(code: => Unit): Unit = apply((() => code): AutoCloseable)
    def close[T <: AnyRef : Releasable](ref: T): Unit
  }
  case class EventuallyTimeout(timeout: Long) extends AnyVal
}
