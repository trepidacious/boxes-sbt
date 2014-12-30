package org.rebeam

import org.vertx.scala.core.Vertx
import org.vertx.scala.core.json.Json
import org.vertx.scala.core.eventbus.Message
import org.vertx.scala.core.json.JsonObject

import scala.concurrent._

class Postgres(address: String, futicle: Futicle) {
  
  val futureBus = futicle.futureBus
  implicit lazy val context = futicle.context 
  
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
  
  def rawOK(command: String) = {
    futureBus.sendFuture[JsonObject, JsonObject](
      address, 
      Json.obj(
        "action" -> "raw",
        "command" -> command
      )    
    ).map(msg => {
      println("command " + command + " -> " + msg.body)
      msg.body.getString("status") == "ok"    
    })
  }
  
  def tableExists(tableName: String) = raw("SELECT true FROM pg_tables WHERE tablename = '" + tableName + "'").map(msg => msg.body.getArray("results").size() > 0)

}