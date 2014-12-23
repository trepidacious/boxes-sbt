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
package com.example

import org.vertx.scala.core._
import org.vertx.scala.core.http.HttpServerRequest
import org.vertx.scala.platform.Verticle

import scala.util.Try

class HelloWorldServer extends Verticle {

  def envInt(s: String) = envString(s).flatMap(i => Try(i.toInt).toOption)
  def envString(s: String) = sys.env.get(s)

  val port = envInt("OPENSHIFT_VERTX_PORT").getOrElse(8080)
  val ip = envString("OPENSHIFT_VERTX_IP").getOrElse("localhost")

  override def start() {
    vertx.createHttpServer.requestHandler { req: HttpServerRequest =>
      req.response.end("Scala verticle deployed using zipped module.\n")
    }.listen(port, ip)
  }

}
