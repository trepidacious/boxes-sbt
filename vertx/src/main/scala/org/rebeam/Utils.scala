package org.rebeam

import javax.xml.bind.DatatypeConverter
import java.security.SecureRandom
import scala.util.Try
import org.vertx.scala.core.json.JsonObject
import org.vertx.scala.core.http.HttpServerRequest

object Utils {

  private val hexArray = "0123456789abcdef".toCharArray()

  private val random = new SecureRandom()
  
  def bytesToHex(bytes: Array[Byte]) = {
    val sb = new StringBuilder()
    for (b <- bytes) {
      val v = b & 0xFF;
      sb.append(hexArray(v >>> 4))
        .append(hexArray(v & 0x0F))
    }
    sb.toString()
  }

  def hexToBytes(hex: String) = DatatypeConverter.parseHexBinary(hex);

  def randomHex(length: Int) = {
    val bytes = new Array[Byte](length)
    random.nextBytes(bytes)
    bytesToHex(bytes)
  }

  def envInt(s: String) = envString(s).flatMap(i => Try(i.toInt).toOption)
  def envString(s: String) = sys.env.get(s)
  
  def envIntWithFallback(s: String, cfg: JsonObject, default: Int) = Option(cfg.getString(s + "Env")).flatMap(envInt(_)).orElse(Option(cfg.getInteger(s)).map(_.toInt)).getOrElse(default)
  def envStringWithFallback(s: String, cfg: JsonObject, default: String) = Option(cfg.getString(s + "Env")).flatMap(envString(_)).orElse(Option(cfg.getString(s))).getOrElse(default)

  def envStringWithFallbackOption(s: String, cfg: JsonObject, default: Option[String]) = Option(cfg.getString(s + "Env")).flatMap(envString(_)).orElse(Option(cfg.getString(s))).orElse(default)

  def cfgBooleanWithFallback(s: String, cfg: JsonObject, default: Boolean) = Option(cfg.getBoolean(s).asInstanceOf[scala.Boolean]).getOrElse(default)
  
  def redirectHTML(url: String) = 
    <html>
      <head>
        <title>Only https is supported.</title>
      </head>
      <body>
        Please use the https protocol to access this resource <a href={url}>here.</a>
      </body>
    </html>

  def redirectHTML() = 
    <html>
      <head>
        <title>Only https is supported.</title>
      </head>
      <body>
        Please use the https protocol to access this resource.
      </body>
    </html>

  def requireHTTPS(requireHTTPSRedirect: Boolean, handler: HttpServerRequest => Unit): HttpServerRequest => Unit = {
    if (requireHTTPSRedirect) requireHTTPS(handler) else handler
  }
  
  def requireHTTPS(handler: HttpServerRequest => Unit): HttpServerRequest => Unit = {
    {req: HttpServerRequest =>
      req.headers.get("x-forwarded-proto") match {
        case Some(s) if s.contains("http") => {
          req.headers.get("host").flatMap(_.headOption) match {
            case Some(host) => {
              val url = "https://" + host + req.uri
              req.response.setStatusCode(301).putHeader("Location", url).end(redirectHTML(url).toString)
            } 
            case _ => {
              req.response.setStatusCode(404).end(redirectHTML().toString)
            }
          }
        } 
        case _ => handler(req)
      }
    }
  }
  
}