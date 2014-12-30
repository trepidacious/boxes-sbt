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
import org.vertx.scala.core.json.JsonObject

/**
 * Launches all verticles in the module
 */
class Launch extends Verticle {

  override def start() {
    
    val appConfig: JsonObject = container.config()

    val postgresConfig = appConfig.getObject("postgresConfig")
    val serverConfig = appConfig.getObject("serverConfig")

    val postgresHost = envStringWithFallback("host", postgresConfig, "localhost")
    val postgresPort = envIntWithFallback("port", postgresConfig, 5432)
    val postgresUsername = envStringWithFallback("username", postgresConfig, System.getProperty("user.name"))
    val postgresPassword = envStringWithFallback("password", postgresConfig, "")
    val postgresDatabase = envStringWithFallback("database", postgresConfig, System.getProperty("user.name"))
    
    postgresConfig.putString("host", postgresHost)
    postgresConfig.putNumber("port", postgresPort)
    postgresConfig.putString("username", postgresUsername)
    postgresConfig.putString("password", postgresPassword)
    postgresConfig.putString("database", postgresDatabase)

    println("Postgres config:\n" + postgresConfig.encodePrettily())
    
    serverConfig.putString("postgresAddress", postgresConfig.getString("address"))
    println("Server config:\n" + serverConfig.encodePrettily())
    
    //Start postgresql module, then our server
    container.deployModule("io.vertx~mod-mysql-postgresql_2.10~0.3.1", postgresConfig, 1, { r: AsyncResult[String] => {
      if (r.succeeded()) {
        container.deployVerticle("scala:org.rebeam.Server", serverConfig, 1, { r: AsyncResult[String] => {
          println("org.rebeam.Server deploy finished")
          if (r.succeeded()) {
            println("Succeeded:")
            println(r.result())
          } else {
            println("Failed:")
            r.cause().printStackTrace()  
          }
        }}) 

      } else r.cause().printStackTrace()  
    }})

  }

}
