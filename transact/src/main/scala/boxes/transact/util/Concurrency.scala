package boxes.transact.util

import java.util.concurrent.locks.ReentrantReadWriteLock
import scala.collection._
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.ThreadFactory
import java.util.concurrent.Executors

class RWLock() {
  private val lock: ReentrantReadWriteLock = new ReentrantReadWriteLock()
  
  def write[T](w: =>T): T = {
    lock.writeLock().lock()
    try {
      return w
    } finally {
      lock.writeLock().unlock()
    }
  }

  def read[T](r: =>T): T = {
    lock.readLock().lock()
    try {
      return r
    } finally {
      lock.readLock().unlock()
    }
  }
}

object RWLock {
  def apply() = new RWLock()
}

class Lock {
  private val lock: ReentrantLock = new ReentrantLock()
  def apply[T](w: =>T): T = run(w)
  def run[T](w: =>T): T = {
    lock.lock()
    try {
      return w
    } finally {
      lock.unlock()
    }
  }
}

object Lock {
  def apply() = new Lock()
}

class DaemonThreadFactory extends ThreadFactory {
  val del = Executors.defaultThreadFactory()
  override def newThread(r: Runnable) = {
    val t = del.newThread(r)
    t.setDaemon(true)
    t
  }
}

object DaemonThreadFactory {
  def apply() = new DaemonThreadFactory()
}
