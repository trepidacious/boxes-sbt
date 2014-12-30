/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rebeam

import org.vertx.scala.core._
import org.vertx.scala.core.http.HttpServerRequest
import org.vertx.scala.core.http.RouteMatcher
import org.vertx.scala.platform.Verticle
import scala.util.Try
import org.vertx.scala.core.json.Json
import Utils._
import org.vertx.scala.core.eventbus.Message
import org.vertx.scala.core.json.JsonObject
import scala.concurrent._
import scala.util.Success
import scala.util.Failure

class Server extends Futicle {

  val ver = 1
  
  override def start() {

    val cfg: JsonObject = container.config()

    val authSecret = cfg.getString("authSecret")
    val port = envIntWithFallback("port", cfg, 8080)
    val host = envStringWithFallback("host", cfg, "localhost")
    val keyStorePassword = cfg.getString("keyStorePassword")
    val postgresAddress = cfg.getString("postgresAddress")

    println("Server on " + host + ":" + port + ", postgres on " + postgresAddress)
    
    val postgres = new Postgres(postgresAddress, futureBus)

    def ivHexExists(ivHex: String) = postgres.prepared("SELECT * FROM iv WHERE iv=?", ivHex).map((message) => message.body.getInteger("rows") > 0)

    def maybeNewIvHex() = {
      val ivHex = randomHex(12)
      println("Trying " + ivHex)
    //    ivHexExists(ivHex).map(exists => if (exists) None else Some(ivHex))
      storeIVHex(ivHex).map(success => if (success) Some(ivHex) else None)
    }
    
    def newIVHex(): Future[String] = {
      maybeNewIvHex.flatMap{
        case Some(ivHex) => {
          println("Got " + ivHex)
          future{ivHex}
        }
        case None => newIVHex()
      }
    }
    
    def storeIVHex(ivHex: String): Future[Boolean] = postgres.insert("iv", Seq("iv"), Seq(ivHex)).map((message) => {
      val status = message.body.getString("status") 
      status == "ok"
    })
    
    val routeMatcher = RouteMatcher()

    routeMatcher.get("/ver", {req: HttpServerRequest => {      
      req.response.end(Json.obj("ver" -> ver).encode())
    }})

    routeMatcher.get("/auth/" + authSecret + "/iv/list", {req: HttpServerRequest => 
      postgres.raw("SELECT iv FROM iv").onComplete{
        case Success(msg) => {
          req.response.end(msg.body.toString())
        }
        case Failure(e) => {
          e.printStackTrace()
          req.response.end
        }
      }
    })

    routeMatcher.get("/auth/" + authSecret + "/iv/new", {req: HttpServerRequest => {
      
      
//      for {
//        insert <- Postgres.insert("iv", Seq("iv"), Seq(ivHex))
//        select <- Postgres.prepared("SELECT * FROM iv WHERE iv=?", ivHex)
//      } {println("Insert: " + insert.body + ", then select: " + select.body)}
      
      newIVHex.onComplete{
        case Success(iv) => req.response.end(Json.obj("iv" -> iv).encode())
        case Failure(e) => {
          e.printStackTrace()
          req.response.end
        }
      }
      
    }})

    val server = vertx.createHttpServer
      .setSSL(true)
      .setKeyStorePath("keystore.jks") 
      .setKeyStorePassword(keyStorePassword)
    
    server.requestHandler(routeMatcher).listen(port, host)

  }

}
