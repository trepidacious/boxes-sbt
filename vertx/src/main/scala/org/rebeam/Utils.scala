package org.rebeam

import javax.xml.bind.DatatypeConverter
import java.security.SecureRandom

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
  
}