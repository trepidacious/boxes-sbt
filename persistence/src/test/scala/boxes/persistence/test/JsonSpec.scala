package boxes.test

import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import boxes._
import boxes.list._
import scala.collection.immutable.Queue
import boxes.persistence._
import boxes.persistence.protobuf.ProtobufTokenWriter
import java.io.ByteArrayOutputStream
import boxes.persistence.protobuf.ProtobufTokenReader
import java.io.ByteArrayInputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.Deflater
import java.util.zip.InflaterInputStream
import com.google.protobuf.CodedOutputStream
import boxes.persistence.json.JSONTokenWriter
import boxes.persistence.json.JSONIO

case class CaseClass(a: String = "a", i: Int = 1)

class JsonSpec extends WordSpec {

  "JsonIO" should {

    "write primitives, case classes, and lists thereof" in {
      val aliases = ClassAliases()
      val io = JSONIO(aliases)

      io.write("a") === "a"
      io.write(1) === "1"
      io.write(1L) === """{"_val_type_":"Long", "_val_":1}"""
      io.write(1.0) === "1.0"
      io.write(1.0f) === """{"_val_type_":"Float", "_val_":1.0}"""
      io.write(CaseClass()) === """{"_type_":"boxes.test.CaseClass","a":"a","i":1}"""
      io.write(List(1, 2, 3)) === "[1,2,3]"
      io.write(List("a", "b", "c")) === """["a","b","c"]"""
      io.write(List(CaseClass("a", 1), CaseClass("b", 2), CaseClass("c", 3))) === """[{"_type_":"boxes.test.CaseClass","a":"a","i":1},{"_type_":"boxes.test.CaseClass","a":"b","i":2},{"_type_":"boxes.test.CaseClass","a":"c","i":3}]"""
    }
  }

}