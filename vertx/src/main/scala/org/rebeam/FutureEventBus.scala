package org.rebeam

import org.vertx.scala.core.eventbus.EventBus
import org.vertx.scala.core.eventbus.Message
import org.vertx.scala.core.eventbus.MessageData
import scala.concurrent._
import scala.language.implicitConversions
import org.vertx.scala.core.Vertx
import java.util.concurrent.Executor

object FutureEventBus {
  def apply(bus: EventBus) = new FutureEventBus(bus)
}

class FutureEventBus(bus: EventBus) {
  def send[ST <% MessageData, RT <% MessageData](address: String, message: ST): Future[Message[RT]] = sendFuture(address, message)

  def sendFuture[ST <% MessageData, RT <% MessageData](address: String, message: ST): Future[Message[RT]] = { 
    val p = promise[Message[RT]]
    bus.send(address, message, (reply: Message[RT]) => p.success(reply))
    p.future
  }
}
