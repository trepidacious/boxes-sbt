package boxes.persistence.mongo

import scala.collection._
import boxes.persistence._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.commons.MongoDBList
import com.mongodb.BasicDBObject
import com.mongodb.casbah.commons.MongoDBObject$
import com.mongodb.casbah.Imports._

//This should probable be called MongoToTokens, it writes Mongo "things" as tokens to a StoringTokenWriter,
//for example so that those tokens can then be converted to a Node, etc.
private class MongoTokenWriting(aliases: ClassAliases) {
  
  val t = new StoringTokenWriter
  
  def tokens = t.tokens

  private[mongo] def writeThing(value: Any) {
    value match {
      case obj: BasicDBObject => writeObj(obj)
      case obj: MongoDBObject => writeObj(obj)
      case arr: BasicDBList => writeArr(arr)
      case arr: MongoDBList => writeArr(arr)
      //Note that we can get Longs from a MongoDBObject, they appear as NumberLong("XXXX") in mongo shell, apparently encoded as BSON int64 type, see http://stackoverflow.com/questions/7682714/does-mongodb-support-floating-point-types 
      case v: Long => t.write(LongToken(v))
      case v: String => t.write(StringToken(v))
      case v: Int => t.write(IntToken(v))
      case v: Double => t.write(DoubleToken(v))
      //Note that we can't get Floats from a MongoDBObject, apparently mongodb can only store Doubles. At some point we could possibly use a wrapper of some sort.
      case v: Boolean => t.write(BooleanToken(v))
      case other:Any => throw new RuntimeException("Unexpected value in MongoDBObject '" + other + "' class '" + other.getClass + "'")
    }
  }
  
  private def writeArr(arr: MongoDBList) {
    t.write(OpenArr)
    arr.foreach(thing => writeThing(thing))
    t.write(CloseArr)
  }
  
  private val skippedFieldNames = Set("_id", "_id_", "_ref_", "_type_")
  
  private def writeObj(obj: MongoDBObject) {
    
    val classNameOption = obj.getAs[String]("_type_")
    val id = obj.getAs[Int]("_id_")
    val ref = obj.getAs[Int]("_ref_")
    classNameOption match {
      case Some(className) => {
        val link = id.map(LinkId(_)).getOrElse(ref.map(LinkRef(_)).getOrElse(LinkEmpty))
        val clazz = aliases.forAlias(className)
        t.write(OpenObj(clazz, link))
        obj.foreach{case (fieldName, value) => {
          if (!skippedFieldNames.contains(fieldName)) {
            t.write(OpenField(fieldName))
            writeThing(value)
          }
        }}
        t.write(CloseObj)
      }
      case None => throw new RuntimeException("No _type_ for an obj")
    }
  }
}

//This should probably be called "TokensToMongo", it reads tokens from a TokenReader, and converts them
//to Mongo data types, which can then be pushed to a MongoDB.
private class MongoTokenReading(t: TokenReader, aliases: ClassAliases) {

  //If next token starts a field, read that field, put it in object o, and return true. Otherwise return false.
  def readField(o: MongoDBObject): Boolean = {
    t.peek match {
      case OpenField(name) => {
        t.pull()
        val v = read()
        o.put(name, v)
        true
      }
      case token:Token => {
        false 
      }
    }
  }

  //If next token can be an Arr element, read that element, put it in list l, and return true. Otherwise return false.
  def readElement(l: MongoDBList): Boolean = {
    t.peek match {
      case CloseArr => false
      case CloseObj => false 
      case OpenField(_) => false 
      case _ => {
        l += read()
        true
      }
    }
  }
  
  def read() = {
    t.pull() match {
      //For now we just reject floats - see above for comment on mongodb not actually being able to store "native" floats, we would need some kind of wrapper.
      case f:FloatToken => throw new RuntimeException("Invalid token, float values cannot be stored in mongodb, please use double values")
      case p:Prim[_] => p.p
      case OpenObj(clazz, link) => {
        val o = new MongoDBObject()
        o.put("_type_", aliases.forClass(clazz))
        link match {
          case LinkId(id) => o.put("_id_", id: Integer)
          case LinkRef(ref) => o.put("_ref_", ref: Integer)
          case LinkEmpty => {}
        }
        while(readField(o)){}
        
        t.pull match {
          case CloseObj => {}
          case token:Token => throw new RuntimeException("Invalid token, expected CloseObj, got " + token)
        }
        o
      }
      case OpenArr => {
        val l = new MongoDBList()
        while (readElement(l)){}
        t.pull match {
          case CloseArr => {}
          case token:Token => throw new RuntimeException("Invalid token, expected CloseArr, got " + token)
        }
        l        
      }
      case token: Token => throw new RuntimeException("Invalid token when expecting top level, got " + token)
    }
  }
  
}

object MongoTokens {
  //TODO - make this stream, so the tokens are made on demand. sending this to
  //mongo in a stream would be the final stage to give minimum overhead for serialisation
  def toTokens(obj: Any, aliases: ClassAliases) = {
    val m = new MongoTokenWriting(aliases)
    m.writeThing(obj)
    new StoringTokenReader(m.tokens:_*)
  }
  
  def toDBO(tokens: TokenReader, aliases: ClassAliases) = {
    new MongoTokenReading(tokens, aliases).read
  }  
}

class StoringTokenWriter extends TokenWriter {
  private val tokensM = mutable.ListBuffer[Token]()
  def write(t: Token) {
    tokensM.append(t)
  }
  def tokens = tokensM.toList
}

class StoringTokenReader(tokens: Token*) extends TokenReader {
  private val tokenStack = mutable.Stack(tokens:_*)
  def peek: Token = tokenStack.head
  def pull(): Token = {
    val t = tokenStack.pop()
//    print(t + ", ")
    t
  }
}

