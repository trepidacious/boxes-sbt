package org.rebeam

import org.vertx.scala.core.Vertx
import org.vertx.scala.core.json.Json
import org.vertx.scala.core.eventbus.Message
import org.vertx.scala.core.json.JsonObject

import scala.concurrent._

class PostgresException(val j: JsonObject) extends Exception(j.encode)

object PostgresException {
  def apply(j: JsonObject) = new PostgresException(j)
}

class Postgres(address: String, futicle: Futicle) {
  
  def fail(j: JsonObject) = Future.failed(PostgresException(j))
  def succeed[T](t: T) = Future.successful(t)
  
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
    ).flatMap(msg => if (msg.body.getString("status") == "ok") succeed(msg.body) else fail(msg.body))
  }
  
  def tableExists(tableName: String) = raw("SELECT true FROM pg_tables WHERE tablename = '" + tableName + "'").map(msg => msg.body.getArray("results").size() > 0)

  def withTable(tableName: String) = 
    raw("SELECT true FROM pg_tables WHERE tablename = '" + tableName + "'")
      .flatMap(msg => if(msg.body.getArray("results").size() > 0) Future.successful(msg.body) else fail(msg.body))
  
}