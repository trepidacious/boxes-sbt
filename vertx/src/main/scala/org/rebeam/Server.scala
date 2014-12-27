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

class Server extends Verticle {

  val ver = 1
  
  def envInt(s: String) = envString(s).flatMap(i => Try(i.toInt).toOption)
  def envString(s: String) = sys.env.get(s)

  val port = envInt("OPENSHIFT_VERTX_PORT").getOrElse(8080)
  val ip = envString("OPENSHIFT_VERTX_IP").getOrElse("localhost")
  
  override def start() {
    println("Server starting...")
    
    val routeMatcher = RouteMatcher()

    routeMatcher.get("/ver", {req: HttpServerRequest => {      
      req.response.end(Json.obj("ver" -> ver).encode())
    }})

    routeMatcher.get("/iv/new", {req: HttpServerRequest => {
      val ivHex = randomHex(12)
      
      vertx.eventBus.send("org.rebeam.psql", 
        Json.obj(
            "action" -> "insert",
            "table" -> "iv",
            "fields" -> Json.arr("iv"),
            "values" -> Json.arr(
                Json.arr(ivHex)
            )          
        ), {message: Message[JsonObject] => println("Response from psql '" + message + "'")}
      )
       
      vertx.eventBus.send("org.rebeam.psql", 
        Json.obj(
            "action" -> "prepared",
            "statement" -> "SELECT * FROM iv WHERE iv=?",
            "values" -> Json.arr(ivHex)          
        ), {message: Message[JsonObject] => println("IVs already in table? '" + message.body + "'")}
      )
      
      val iv = Json.obj("iv" -> ivHex)
      
      req.response.end(iv.encode())
    }})

    vertx.createHttpServer.requestHandler(routeMatcher).listen(port, ip)
    println("Server STARTED")

  }

}
