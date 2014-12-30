package org.rebeam

import org.vertx.scala.platform.Verticle

class Futicle extends Verticle {
  implicit lazy val context = FutureEventBus.context()
  implicit lazy val futureBus = FutureEventBus(vertx.eventBus)
}