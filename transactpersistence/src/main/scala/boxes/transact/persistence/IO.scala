package boxes.transact.persistence

import java.io.{InputStream, OutputStream}
import boxes.Box
import boxes.transact.util.NamingBiMap
import boxes.persistence.TokenReader
import boxes.persistence.TokenWriter
import boxes.transact.TxnR
import boxes.transact.Txn
import boxes.persistence.json.JSONTokenReader
import boxes.persistence.json.JSONTokenWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import boxes.persistence.DataFactory
import boxes.persistence.ClassAliases
import java.io.StringWriter
import boxes.persistence.mongo.MongoTokens
import boxes.persistence.mongo.StoringTokenWriter
import boxes.persistence.mongo.StoringTokenReader
import java.io.StringReader
import boxes.persistence.xml.XMLTokenReader
import boxes.persistence.xml.XMLTokenWriter
import scala.io.Source


class IO(val dataFactory: DataFactory, val aliases: ClassAliases = ClassAliases()) {

  val codecByClass = new CodecByClass

  def write(t:Any, output:OutputStream)(implicit txn: TxnR) = {
    //Code as a transaction, to prevent concurrent modification
    val target = dataFactory.writer(output, aliases)
    codecByClass.write(t, target)
    target.close
  }

  def read(input:InputStream)(implicit txn: Txn) = {
    //Decode, so we run as a transaction, AND reactions are handled properly
    val source = dataFactory.reader(input, aliases)
    val t = codecByClass.read(source)
    source.close
    t
  }

  def alias(c:Class[_], s:String) = aliases.alias(c, s)

  def add(codec:CodecWithClass[_]) {
    codecByClass.add(codec)
  }

  def add(codec:Codec[_], clazz:Class[_]) {
    codecByClass.add(codec, clazz)
  }

}

object IO {
  def xml(aliases:ClassAliases = new ClassAliases): XMLIO = new XMLIO(aliases)
  def json(aliases:ClassAliases = new ClassAliases): JSONIO = new JSONIO(aliases)
}

object JSONDataFactory extends DataFactory {
  def reader(input:InputStream, aliases:ClassAliases) = new JSONTokenReader(new InputStreamReader(input, "UTF-8"), aliases)
  def writer(output:OutputStream, aliases:ClassAliases) = new JSONTokenWriter(new OutputStreamWriter(output, "UTF-8"), aliases)
}

class JSONIO(aliases:ClassAliases) extends IO(JSONDataFactory, aliases) {
  def write(t: Any)(implicit txn: TxnR): String = {
    val s = new StringWriter()
    val w = new JSONTokenWriter(s, aliases)
    codecByClass.write(t, w)
    w.close
    s.toString()
  }
  
  def read(s: String)(implicit txn: Txn) = {
    //Decode, so we run as a transaction, AND reactions are handled properly
    val source = new JSONTokenReader(new StringReader(s), aliases)
    val t = codecByClass.read(source)
    source.close
    t
  }

  def readDBO(dbo: Any)(implicit txn: Txn) = {
    val r = MongoTokens.toTokens(dbo, aliases)
    //Decode, so we run as a transaction, AND reactions are handled properly
    val t = codecByClass.read(r)
    r.close
    t
  }

  def writeDBO(t: Any)(implicit txn: TxnR) = {
    val w = new StoringTokenWriter
    codecByClass.write(t, w)
    w.close()
    MongoTokens.toDBO(new StoringTokenReader(w.tokens:_*), aliases)
  }

}

object JSONIO {
  def apply(aliases:ClassAliases = new ClassAliases): JSONIO = new JSONIO(aliases)
}




object XMLDataFactory extends DataFactory {
  def reader(input:InputStream, aliases:ClassAliases) = new XMLTokenReader(Source.fromInputStream(input, "UTF-8"), aliases)
  def writer(output:OutputStream, aliases:ClassAliases) = new XMLTokenWriter(new OutputStreamWriter(output, "UTF-8"), aliases)
}

class XMLIO(aliases:ClassAliases) extends IO(XMLDataFactory, aliases) {
  def write(t: Any)(implicit txn: TxnR): String = {
    val s = new StringWriter()
    val w = new XMLTokenWriter(s, aliases)
    Box.transact {
      codecByClass.write(t, w)
      w.close
    }
    s.toString()
  }
  
  def read(s: String)(implicit txn: Txn) = {
    //Decode, so we run as a transaction, AND reactions are handled properly
    Box.decode {
      val source = new XMLTokenReader(Source.fromString(s), aliases)
      val t = codecByClass.read(source)
      source.close
      t
    }
  }
}

object XMLIO {
  def apply(aliases:ClassAliases = new ClassAliases): XMLIO = new XMLIO(aliases)
}
