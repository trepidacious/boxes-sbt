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

class P extends Node {
  val name = Var("name")
  val age = Var(32)
  val friend:Var[Option[P]] = Var(None)
  val spouse:Var[Option[P]] = Var(None)
  val numbers = Var(List[Int]())
  val accounts = Var(Map[String, Int]())

  override def toString = "'" + name() + "', " + age() + ", friend: " + friend() + ", spouse " + spouse() + ", numbers " + numbers() + ", accounts " + accounts()
}

class ProtobufSpec extends WordSpec {


  "Protobuf" should {

    "work in simple case" in {
      val bob = new P
      bob.name() = "Bob"
      bob.age() = 42
      
      val bill = new P
      bill.name() = "Bill"
      bill.age() = 37
      
      bob.friend() = Some(bill)
//      bill.friend() = Some(bob)
      
      bob.numbers() = List(1,2,3)
      bill.numbers() = List(2,4,6)
      
      bob.accounts() = Map("current"->420, "savings" -> 10000)
      bill.accounts() = Map("secret swiss"->42000000)
      
      bob.spouse() = Some(bill)
      
      println(bob)
      
      //bob friend and spouse are both bill, and are hence the same instance of P
      assert(bob.friend().get eq bob.spouse().get)
      
      val encode = new CodecByClass()
      val bos = new ByteArrayOutputStream

      val aliases = ClassAliases()
      aliases.alias(classOf[P], "P")
      val writer = ProtobufTokenWriter(bos, aliases)

      encode.write(bob, writer)
      writer.close
      
      println()
      println(bos.toByteArray.length + " bytes:")
      val l = List(bos.toByteArray():_*)
      l.foreach(b => print((b&0xFF) + " "))
      println("\n")
      
      
      val bis = new ByteArrayInputStream(bos.toByteArray())
      val reader = ProtobufTokenReader(bis, aliases)
      val bob2 = encode.read(reader).asInstanceOf[P]
      
      println(bob2)
      
      assert(bob.toString.equals(bob2.toString))
      
      //bob 2's friend and spouse are both bill2, and are hence the same instance of P
      //This checks that we used references correctly to recreate a single instance of Bill, not two equal instances.
      assert(bob2.friend().get eq bob2.spouse().get)
    }
  }

}