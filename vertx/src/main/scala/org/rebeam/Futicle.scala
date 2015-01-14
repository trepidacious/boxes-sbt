package org.rebeam

import org.vertx.scala.platform.Verticle
import scala.concurrent._
import java.util.concurrent.Executor

object Futicle {
    lazy val context = {
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
}

class Futicle extends Verticle {
  implicit lazy val context = Futicle.context
  implicit lazy val futureBus = FutureEventBus(vertx.eventBus)
  implicit lazy val futureContainer = FutureContainer(container)
}