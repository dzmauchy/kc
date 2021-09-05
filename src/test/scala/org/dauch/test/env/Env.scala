package org.dauch.test.env

import com.typesafe.scalalogging.StrictLogging
import org.dauch.test.env.Env.{Closer, EventuallyTimeout}

import java.io.File
import java.nio.file.{Files, NotDirectoryException, Path}
import java.util.concurrent.{ConcurrentLinkedDeque, ExecutorService, TimeUnit}
import scala.concurrent.TimeoutException
import scala.reflect.io.Directory
import scala.util.Using
import scala.util.Using.Releasable
import scala.util.control.NonFatal

trait Env extends StrictLogging {

  def before(): Unit = ()
  def after(): Unit = ()

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

  def release(f: Closer => Unit): Unit = {
    val rss = new ConcurrentLinkedDeque[(AnyRef, Releasable[AnyRef])]()
    val closer = new Closer {
      override def apply[T <: AnyRef : Releasable](f: => T): T = {
        val r = f
        if (r != null) {
          rss.add(r -> implicitly[Releasable[T]].asInstanceOf[Releasable[AnyRef]])
        }
        r
      }
    }
    try {
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
  trait Closer {
    def apply[T <: AnyRef : Releasable](f: => T): T
    def register(code: => Unit): Unit = apply((() => code): AutoCloseable)
  }
  case class EventuallyTimeout(timeout: Long) extends AnyVal
}
