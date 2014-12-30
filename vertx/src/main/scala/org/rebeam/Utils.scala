package org.rebeam

import javax.xml.bind.DatatypeConverter
import java.security.SecureRandom
import scala.util.Try
import org.vertx.scala.core.json.JsonObject

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
  
  def envIntWithFallback(s: String, cfg: JsonObject, default: Int) = Option(cfg.getString(s + "Env")).flatMap(envInt(_)).orElse(Option(cfg.getInteger(s).toInt)).getOrElse(default)
  def envStringWithFallback(s: String, cfg: JsonObject, default: String) = Option(cfg.getString(s + "Env")).flatMap(envString(_)).orElse(Option(cfg.getString(s))).getOrElse(default)

}