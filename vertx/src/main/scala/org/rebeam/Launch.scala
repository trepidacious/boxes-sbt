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
class Launch extends Futicle {

  def processPostgresConfig(config: JsonObject) {

    //TODO refactor, e.g. implicit RichJsonObject with methods like envFallback("host", "localhost").
    val host = envStringWithFallback("host", config, "localhost")
    val port = envIntWithFallback("port", config, 5432)
    val username = envStringWithFallback("username", config, System.getProperty("user.name"))
    val password = envStringWithFallback("password", config, "")
    val database = envStringWithFallback("database", config, System.getProperty("user.name"))

    config.putString("host", host)
    config.putNumber("port", port)
    config.putString("username", username)
    config.putString("password", password)
    config.putString("database", database)
  }

  def processMongodbConfig(config: JsonObject) {

    val host = envStringWithFallback("host", config, "localhost")
    val port = envIntWithFallback("port", config, 27017)
    val username = envStringWithFallbackOption("username", config, None)
    val password = envStringWithFallbackOption("password", config, None)
    val database = envStringWithFallback("db_name", config, "vertx")

    config.putString("host", host)
    config.putNumber("port", port)
    username.foreach(config.putString("username", _))
    password.foreach(config.putString("password", _))
    config.putString("db_name", database)
    config.putBoolean("use_objectids", true)
    config.putBoolean("use_mongo_types", true)
  }

  override def start() {

    val appConfig: JsonObject = container.config()

    val postgresConfig = appConfig.getObject("postgresConfig")
    val mongodbConfig = appConfig.getObject("mongodbConfig")
    val serverConfig = appConfig.getObject("serverConfig")

    processPostgresConfig(postgresConfig)
    println("Postgres config:\n" + postgresConfig.encodePrettily())

    processMongodbConfig(mongodbConfig)
    println("Mongodb config:\n" + mongodbConfig.encodePrettily())

    serverConfig.putString("postgresAddress", postgresConfig.getString("address"))
    serverConfig.putString("mongodbAddress", mongodbConfig.getString("address"))
    println("Server config:\n" + serverConfig.encodePrettily())

    for {
      postgresDeployId <- futureContainer.deployModule("io.vertx~mod-mysql-postgresql_2.10~0.3.1", postgresConfig)
      mongodbDeployId <- futureContainer.deployModule("io.vertx~mod-mongo-persistor~2.1.1-SNAPSHOT", mongodbConfig)
      serverDeployId <- futureContainer.deployVerticle("scala:org.rebeam.Server", serverConfig)
    } {
      println("Deployed postgres as " + postgresDeployId + ", mongodb as " + mongodbDeployId + " and server as " + serverDeployId)
    }

  }

}
