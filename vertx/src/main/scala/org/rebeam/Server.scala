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
import org.vertx.scala.core.http.ServerWebSocket
import org.vertx.scala.core.streams.Pump
import org.vertx.scala.core.sockjs.SockJSSocket

class Server extends Futicle {

  val ver = 1

  lazy val cfg: JsonObject = container.config()

  lazy val authSecret           = cfg.getString("authSecret")
  lazy val port                 = envIntWithFallback("port", cfg, 8080)
  lazy val host                 = envStringWithFallback("host", cfg, "localhost")
  lazy val keyStorePassword     = Option(cfg.getString("keyStorePassword"))
  lazy val postgresAddress      = cfg.getString("postgresAddress")
  lazy val requireHTTPSRedirect = cfgBooleanWithFallback("requireHTTPSRedirect", cfg, false)
  
  lazy val postgres = new Postgres(postgresAddress, this)

  def ivHexExists(ivHex: String) = postgres.prepared("SELECT * FROM iv WHERE iv=?", ivHex).map(_.body.getInteger("rows") > 0)

  def maybeNewIvHex() = {
    val ivHex = randomHex(12)
    println("Trying " + ivHex)
  //    ivHexExists(ivHex).map(exists => if (exists) None else Some(ivHex))
    storeIVHex(ivHex).map(if (_) Some(ivHex) else None)
  }
  
  def newIVHex(): Future[String] = {
    maybeNewIvHex.flatMap{
      case Some(ivHex) => {
        println("Got " + ivHex)
        Future.successful(ivHex)
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

    routeMatcher.get("/sockdemo", {req: HttpServerRequest => {
      req.response.sendFile("sockdemo/sockdemo.html")
//      req.response.end("SockJS Demo")
    }})

    routeMatcher.get("/auth/" + authSecret + "/iv/list", requireHTTPS(requireHTTPSRedirect, {req: HttpServerRequest => 
      postgres.raw("SELECT iv FROM iv").onComplete{
        case Success(msg) => {
          req.response.end(msg.body.toString())
        }
        case Failure(e) => {
          e.printStackTrace()
          req.response.end
        }
      }
    }))

    routeMatcher.get("/auth/" + authSecret + "/iv/new", requireHTTPS(requireHTTPSRedirect, {req: HttpServerRequest => 
      newIVHex.onComplete{
        case Success(iv) => req.response.end(Json.obj("iv" -> iv).encode())
        case Failure(e) => {
          e.printStackTrace()
          req.response.end
        }
      }
    }))

    val server = vertx.createHttpServer
    
    //Use ssl if we have a password. This is not needed on openshift, 
    //which has an SSL proxy that calls through to application http 
    //(and hence provides its own certificate etc.), and is also not 
    //needed for dev., but could be enabled when deploying for production 
    //on another host.
    keyStorePassword.foreach(p => {
      server
        .setSSL(true)
        .setKeyStorePath("keystore.jks") 
        .setKeyStorePassword(p)      
    })
    
    server.requestHandler(routeMatcher)
    
//    val wsHandler: (ServerWebSocket => Unit) = (ws: ServerWebSocket) => { 
//      Pump.createPump(ws, ws).start()
//    }
//    Chain .websocketHandler(wsHandler) after requestHandler in server...listen() below
    
    val sockServer = vertx.createSockJSServer(server)
    
    val config = Json.obj("prefix" -> "/sock")

    sockServer.installApp(config, { sock: SockJSSocket =>
      Pump.createPump(sock, sock).start()
    })
    
    server.listen(port, host)
  }
  
  override def start() {

    println("Server on " + host + ":" + port + ", postgres on " + postgresAddress)
    
    //FIXME use a transaction to ensure that index is created.
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
