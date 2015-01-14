package org.rebeam

import org.vertx.scala.core.eventbus.EventBus
import org.vertx.scala.core.eventbus.Message
import org.vertx.scala.core.eventbus.MessageData
import scala.concurrent._
import scala.language.implicitConversions
import org.vertx.scala.core.Vertx
import java.util.concurrent.Executor
import org.vertx.scala.platform.Container
import org.vertx.scala.core.json.JsonObject
import org.vertx.scala.core.json.Json
import org.vertx.java.core.AsyncResult
import org.vertx.java.core.Handler

object FutureContainer {
  
  def apply(container: Container) = new FutureContainer(container)
}

class FutureContainer(container: Container) {
  
  private def asyncResultHandler[T](p: Promise[T]) = new Handler[AsyncResult[T]]() {
    override def handle(result: AsyncResult[T]) {
      if (result.result() != null) {
        p.success(result.result())
      } else {
        p.failure(result.cause())
      }
    }
  }
  
  /**
   * Deploy a module programmatically
   * @param name The main of the module to deploy
   * @param config JSON config to provide to the module
   * @param instances The number of instances to deploy (defaults to 1)
   * @return Future with the unique deployment id
   */
  def deployModule(name: String, config: JsonObject = Json.emptyObj(), instances: Int = 1) = {
    val p = promise[String]
    container.asJava.deployModule(name, config, instances, asyncResultHandler(p))
    p.future
  }

  /**
   * Deploy a verticle programmatically
   * @param name The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   * @return Future with the unique deployment id
   */
  def deployVerticle(name: String, config: JsonObject = Json.emptyObj(), instances: Int = 1) = {
    val p = promise[String]
    container.asJava.deployVerticle(name, config, instances, asyncResultHandler(p))
    p.future
  }

  /**
   * Deploy a worker verticle programmatically
   * @param name The main of the verticle
   * @param config JSON config to provide to the verticle (defaults to empty JSON)
   * @param instances The number of instances to deploy (defaults to 1)
   * @param multiThreaded if true then the verticle will be deployed as a multi-threaded worker (default is false)
   * @return Future with the unique deployment id
   */
  def deployWorkerVerticle(name: String, config: JsonObject = Json.emptyObj(), instances: Int = 1, multiThreaded: Boolean = false) = {
    val p = promise[String]
    container.asJava.deployWorkerVerticle(name, config, instances, multiThreaded, asyncResultHandler(p))
    p.future
  }

  /**
   * Undeploy a module
   * @param deploymentID The deployment ID
   * @return Future that will succeed or fail when undeployment succeeds or fails
   */
  def undeployModule(deploymentID: String) = {
    val p = promise[Void]
    container.asJava.undeployModule(deploymentID, asyncResultHandler(p))
    p.future
  }

  /**
   * Undeploy a module
   * @param deploymentID The deployment ID
   * @return Future that will succeed or fail when undeployment succeeds or fails
   */
  def undeployVerticle(deploymentID: String) = {
    val p = promise[Void]
    container.asJava.undeployVerticle(deploymentID, asyncResultHandler(p))
    p.future
  }
  
}
