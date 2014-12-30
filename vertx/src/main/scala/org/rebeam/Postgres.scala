package org.rebeam

import org.vertx.scala.core.Vertx
import org.vertx.scala.core.json.Json
import org.vertx.scala.core.eventbus.Message
import org.vertx.scala.core.json.JsonObject

import scala.concurrent._

class Postgres(address: String, futureBus: FutureEventBus) {
  
  def insert(table: String, fields: Seq[String], values: Seq[Any]*) = {
    futureBus.sendFuture[JsonObject, JsonObject](
      address, 
      Json.obj(
        "action" -> "insert",
        "table" -> table,
        "fields" -> Json.arr(fields: _*),
        "values" -> Json.arr(values: _*)
      )    
    )    
  }
  
  def prepared(statement: String, values: Any*) = {
    futureBus.sendFuture[JsonObject, JsonObject](
      address, 
      Json.obj(
        "action" -> "prepared",
        "statement" -> statement,
        "values" -> Json.arr(values: _*)
      )
    )
  }

  def raw(command: String) = {
    futureBus.sendFuture[JsonObject, JsonObject](
      address, 
      Json.obj(
        "action" -> "raw",
        "command" -> command
      )    
    )    
  }

}