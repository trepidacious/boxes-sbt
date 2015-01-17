package org.rebeam

import org.vertx.scala.core.Vertx
import org.vertx.scala.core.json.Json
import org.vertx.scala.core.eventbus.Message
import org.vertx.scala.core.json.JsonObject
import scala.concurrent._
import org.vertx.scala.core.eventbus.MessageData

class MongodbException(val e: String) extends Exception(e)

object MongodbException {
  def apply(e: String) = new MongodbException(e)
}

class Mongodb(address: String, futicle: Futicle) {
  
  def fail(e: String) = Future.failed(MongodbException(e))
  def succeed[T](t: T) = Future.successful(t)
  
  val futureBus = futicle.futureBus
  implicit lazy val context = futicle.context 
  
  def send(message: JsonObject) = futureBus.sendFuture[JsonObject, JsonObject](address, message)
  
  def find(collection: String) = {
    send(Json.obj(
        "action" -> "find",
        "collection" -> collection
    )).flatMap(reply => {
      if (reply.body.getString("status") == "ok") {
        succeed(reply.body.getArray("results"))
      } else {
        fail(reply.body.getString("message"))
      }
    })
  }

  def save(collection: String, document: JsonObject) = {
    send(Json.obj(
        "action" -> "save",
        "collection" -> collection,
        "document" -> document
    )).flatMap(reply => {
      if (reply.body.getString("status") == "ok") {
        succeed(Option(reply.body.getObject("_id")))
      } else {
        fail(reply.body.getString("message"))
      }
    })
  }

  
//  def insert(table: String, fields: Seq[String], values: Seq[Any]*) = {
//    futureBus.sendFuture[JsonObject, JsonObject](
//      address, 
//      Json.obj(
//        "action" -> "insert",
//        "table" -> table,
//        "fields" -> Json.arr(fields: _*),
//        "values" -> Json.arr(values: _*)
//      )    
//    )    
//  }
//  
//  def prepared(statement: String, values: Any*) = {
//    futureBus.sendFuture[JsonObject, JsonObject](
//      address, 
//      Json.obj(
//        "action" -> "prepared",
//        "statement" -> statement,
//        "values" -> Json.arr(values: _*)
//      )
//    )
//  }
//
//  def raw(command: String) = {
//    futureBus.sendFuture[JsonObject, JsonObject](
//      address, 
//      Json.obj(
//        "action" -> "raw",
//        "command" -> command
//      )    
//    )    
//  }
//  
//  def rawOK(command: String) = {
//    futureBus.sendFuture[JsonObject, JsonObject](
//      address, 
//      Json.obj(
//        "action" -> "raw",
//        "command" -> command
//      )    
//    ).flatMap(msg => if (msg.body.getString("status") == "ok") succeed(msg.body) else fail(msg.body))
//  }
//  
//  def tableExists(tableName: String) = raw("SELECT true FROM pg_tables WHERE tablename = '" + tableName + "'").map(msg => msg.body.getArray("results").size() > 0)
//
//  def withTable(tableName: String) = 
//    raw("SELECT true FROM pg_tables WHERE tablename = '" + tableName + "'")
//      .flatMap(msg => if(msg.body.getArray("results").size() > 0) Future.successful(msg.body) else fail(msg.body))
//  
}