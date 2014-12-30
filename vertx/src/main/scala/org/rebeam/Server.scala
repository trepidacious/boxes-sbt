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

  lazy val cfg: JsonObject = container.config()

  lazy val authSecret = cfg.getString("authSecret")
  lazy val port = envIntWithFallback("port", cfg, 8080)
  lazy val host = envStringWithFallback("host", cfg, "localhost")
  lazy val keyStorePassword = cfg.getString("keyStorePassword")
  lazy val postgresAddress = cfg.getString("postgresAddress")

  lazy val postgres = new Postgres(postgresAddress, this)

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
  
  def startServer() {
    
    val routeMatcher = RouteMatcher()

    routeMatcher.get("/", {req: HttpServerRequest => {      
      req.response.end("Vertx Demo")
    }})

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
      newIVHex.onComplete{
        case Success(iv) => req.response.end(Json.obj("iv" -> iv).encode())
        case Failure(e) => {
          e.printStackTrace()
          req.response.end
        }
      }
    }})

    val server = vertx.createHttpServer
    
// Can enable https - this is not needed on openshift, which has an SSL proxy that calls through to
// application http (and hence provides its own certificate etc.), and is also not needed for dev., but
// could be enabled when deploying for production on another host.
//      .setSSL(true)
//      .setKeyStorePath("keystore.jks") 
//      .setKeyStorePassword(keyStorePassword)
    
    server.requestHandler(routeMatcher).listen(port, host)
  }
  
  override def start() {

    println("Server on " + host + ":" + port + ", postgres on " + postgresAddress)
    
    //Initialise database if not there already
    val makeIV = postgres.withTable("iv").fallbackTo(
      postgres.rawOK("""
          create table iv(
            key serial primary key, 
            iv text not null
          );
      """).flatMap(_ => postgres.rawOK("create unique index on iv (iv);"))
    )
    makeIV.onSuccess{case msg => startServer}
  }

}
