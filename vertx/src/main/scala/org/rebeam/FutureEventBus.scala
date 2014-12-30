package org.rebeam

import org.vertx.scala.core.eventbus.EventBus
import org.vertx.scala.core.eventbus.Message
import org.vertx.scala.core.eventbus.MessageData
import scala.concurrent._
import scala.language.implicitConversions
import org.vertx.scala.core.Vertx
import java.util.concurrent.Executor

object FutureEventBus {
  def context() = {
    ExecutionContext.fromExecutor(
      new Executor {
        override def execute(command: Runnable) {
          //This may seem odd - we just run the code in the current thread.
          //However we know that the promises used for the FutureEventBus 
          //are always completed by handlers called in the vert.x 
          //eventloop thread, and we then want the responses
          //to the completed futures to run in the same thread, so we can
          //just call them straight away. In this case we are using Futures
          //to neaten up the interface to event handlers, which are already
          //running in the correct thread, so no real executor is needed.
          //Note however that this executor is no use at all for running code
          //in another thread, or asynchronously - we rely on the event bus to
          //do that for us, it already has asynchonicity built in, we are just 
          //neatening up composing callbacks
          command.run()
          //TODO: Might be able to do this using vertx.currentContext().runOnContext()
        }
      }
    )
  }
  
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
