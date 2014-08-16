package boxes.transact.persistence

import boxes.persistence.TokenReader
import boxes.persistence.TokenWriter
import boxes.transact._
import boxes.transact.node._
import com.novus.salat._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.ISODateTimeFormat
import boxes.persistence.OpenObj
import boxes.persistence.BooleanToken
import boxes.persistence.LongToken
import boxes.persistence.OpenField
import boxes.persistence.FloatToken
import boxes.persistence.Cached
import boxes.persistence.DoubleToken
import boxes.persistence.LinkEmpty
import boxes.persistence.LinkId
import boxes.persistence.IntToken
import boxes.persistence.OpenArr
import boxes.persistence.CloseObj
import boxes.persistence.CloseArr
import boxes.persistence.New
import boxes.persistence.LinkRef
import boxes.persistence.StringToken
import scala.collection.immutable.SortedMap

/**
* Writes objects to a TokenWriter, and
* reads them from a TokenReader. Reading and writing
* use transactions.
*/
trait Codec[T] {
  def read(reader : TokenReader)(implicit txn: Txn) : T
  def write(t : T, writer : TokenWriter)(implicit txn: TxnR) : Unit
}

trait CodecWithClass[T] extends Codec[T]{
  //Note this isn't Class[T] because of type erasure
  def clazz():Class[_]
}


class CodecByClass extends Codec[Any] {

  private val root = new CodecNode(AnyCodec, classOf[Any])
  private val codecs = scala.collection.mutable.Set[Codec[_]]()

  {
    //Common default codecs
    add(ValCodecs.IntCodec)
    add(ValCodecs.LongCodec)
    add(ValCodecs.FloatCodec)
    add(ValCodecs.DoubleCodec)
    add(ValCodecs.BooleanCodec)
    add(ValCodecs.StringCodec)

    add(new ListCodec(this), classOf[List[_]])
    add(new MapCodec(this), classOf[Map[_,_]])
    add(new SetCodec(this), classOf[Set[_]])
    add(new NodeCodec(this), classOf[Node])
    add(new CaseClassCodec(this), classOf[Product])
    add(new OptionCodec(this), classOf[Option[_]])
    add(new DateTimeCodec(this), classOf[DateTime])
  }

  def add(codec:CodecWithClass[_]):Unit = {
    add(codec, codec.clazz)
  }

  def add(codec:Codec[_], clazz:Class[_]):Unit = {
    //Don't add the same codec twice
    if (!codecs.contains(codec)) {
      codecs.add(codec)

      //Add a new node under the most specific superclass of the new node
      mostSpecific(root, clazz).subNodes.append(new CodecNode(codec, clazz))
    }
  }

  def get(clazz:Class[_]) = mostSpecific(root, clazz).codec

  //Need to look up class from tag, then use appropriate codec
  override def read(reader : TokenReader)(implicit txn: Txn) = {
    val t = reader.peek
    val c = t match {
      case OpenObj(clazz, _)           => clazz
      case BooleanToken(p: Boolean)    => ValCodecs.BooleanCodec.clazz 
      case IntToken(p: Int)            => ValCodecs.IntCodec.clazz 
      case LongToken(p: Long)          => ValCodecs.LongCodec.clazz 
      case FloatToken(p: Float)        => ValCodecs.FloatCodec.clazz 
      case DoubleToken(p: Double)      => ValCodecs.DoubleCodec.clazz 
      case StringToken(p: String)      => ValCodecs.StringCodec.clazz 
      case OpenArr                      => ListCodec.clazz 

      case _ => throw new RuntimeException("Expected OpenObj, OpenArr, or value Token got " + t)
    }
    
    val codec = get(c)
    codec.read(reader)
  }

  override def write(t : Any, writer: TokenWriter)(implicit txn: TxnR) = {
    val tClass = t.asInstanceOf[AnyRef].getClass
    get(tClass).asInstanceOf[Codec[Any]].write(t, writer)
  }

  private def mostSpecific(node:CodecNode, clazz:Class[_]):CodecNode = {
    node.subNodes.find(subNode => subNode.clazz.isAssignableFrom(clazz)) match {
      case None => node
      case Some(suitableSubNode) => mostSpecific(suitableSubNode, clazz)
    }
  }

  private class CodecNode(val codec:Codec[_], val clazz:Class[_]) {
    val subNodes = scala.collection.mutable.ArrayBuffer[CodecNode]()
  }

}


object AnyCodec extends Codec[Any] {
  override def read(reader: TokenReader)(implicit txn: Txn) = throw new RuntimeException("Can't read Any")
  override def write(t : Any, writer: TokenWriter)(implicit txn: TxnR) = throw new RuntimeException("Can't write Any, instance " + t)
}

class OptionCodec(delegate:Codec[Any]) extends Codec[Option[_]] {
  override def read(reader: TokenReader)(implicit txn: Txn) = {
    reader.pullAndAssert(OpenObj(classOf[Option[_]]))
    
    val t = reader.pull
    t match {
      case CloseObj => None            //None is just an empty Option obj
      case OpenField("Some") => {        //Some has a single field, "Some". Remember to get the object close tag afterwards
        val s = Some(delegate.read(reader))
        reader.pullAndAssert(CloseObj)
        s
      }
      case _ => throw new RuntimeException("Expected CloseObj or OpenField(Some), got " + t)
    }
  }
  override def write(o : Option[_], writer: TokenWriter)(implicit txn: TxnR) = {
    writer.write(OpenObj(classOf[Option[_]]))
    o match {
      case None => {}
      case Some(s) => {
        writer.write(OpenField("Some"))
        delegate.write(s, writer)
      }
    }
    writer.write(CloseObj)
  }
}


object DateTimeCodec {
  val formatter = ISODateTimeFormat.dateTime()
}

class DateTimeCodec(delegate:Codec[Any]) extends Codec[DateTime] {
  override def read(reader: TokenReader)(implicit txn: Txn) = {
    reader.pullAndAssert(OpenObj(classOf[DateTime]))
    reader.pullAndAssert(OpenField("iso8601"))
    reader.pull match {
      case StringToken(iso) => {
        reader.pullAndAssert(CloseObj)
        new DateTime(iso)    
      }
      case _ => throw new RuntimeException("Expected String token")
    }
  }
  override def write(dt : DateTime, writer: TokenWriter)(implicit txn: TxnR) = {
    writer.write(OpenObj(classOf[DateTime]))
    writer.write(OpenField("iso8601"))
    writer.write(StringToken(DateTimeCodec.formatter.print(dt)))
    writer.write(CloseObj)
  }
}

object ListCodec {
  val clazz = classOf[List[_]]
}
class ListCodec(delegate:Codec[Any]) extends CodecWithClass[List[_]] {
  override def read(reader: TokenReader)(implicit txn: Txn) = {
    reader.pullAndAssert(OpenArr)
    val lb = scala.collection.mutable.ListBuffer[Any]()
    while (reader.peek != CloseArr) {
      lb.append(delegate.read(reader))
    }
    reader.pullAndAssert(CloseArr)
    lb.toList
  }
  override def write(list : List[_], writer: TokenWriter)(implicit txn: TxnR) = {
    writer.write(OpenArr)
    list.foreach(e => delegate.write(e, writer))
    writer.write(CloseArr)
  }
  override def clazz = ListCodec.clazz
}

class SetCodec(delegate:Codec[Any]) extends CodecWithClass[Set[_]] {
  override def read(reader: TokenReader)(implicit txn: Txn) = {
  reader.pullAndAssert(OpenObj(classOf[Set[_]]))
    reader.pullAndAssert(OpenField("elements"))
    reader.pullAndAssert(OpenArr)
    val lb = scala.collection.mutable.ListBuffer[Any]()
    while (reader.peek != CloseArr) {
      lb.append(delegate.read(reader))
    }
    reader.pullAndAssert(CloseArr)
    reader.pullAndAssert(CloseObj)
    Set(lb:_*)
  }
  override def write(set : Set[_], writer : TokenWriter)(implicit txn: TxnR) = {
    writer.write(OpenObj(classOf[Set[_]]))
    writer.write(OpenField("elements"))
    writer.write(OpenArr)
    set.foreach(e => delegate.write(e, writer))
    writer.write(CloseArr)
    writer.write(CloseObj)
  }
  override def clazz = classOf[Set[_]]
}

class MapCodec(delegate:Codec[Any]) extends CodecWithClass[Map[_,_]] {
  override def read(reader : TokenReader)(implicit txn: Txn) = {
    val entries = scala.collection.mutable.ListBuffer[(Any,Any)]()
    reader.pullAndAssert(OpenObj(classOf[Map[_, _]]))
    reader.pullAndAssert(OpenField("entries"))
    reader.pullAndAssert(OpenArr)
    while (reader.peek != CloseArr) {
      reader.pullAndAssert(OpenArr)
      val key = delegate.read(reader)
      val value = delegate.read(reader)
      entries.append((key, value))
      reader.pullAndAssert(CloseArr)
    }
    reader.pullAndAssert(CloseArr)
    reader.pullAndAssert(CloseObj)

    Map(entries:_*)
  }

  override def write(map : Map[_,_], writer: TokenWriter)(implicit txn: TxnR) = {
    writer.write(OpenObj(classOf[Map[_,_]]))
    writer.write(OpenField("entries"))
    writer.write(OpenArr)
    map.foreach(entry => {
      writer.write(OpenArr)
      delegate.write(entry._1, writer)
      delegate.write(entry._2, writer)
      writer.write(CloseArr)
    })
    writer.write(CloseArr)
    writer.write(CloseObj)
  }
  override def clazz = classOf[Map[_, _]]
}


class NodeCodec(delegate:Codec[Any]) extends Codec[Node] {
  
  override def read(reader: TokenReader)(implicit txn: Txn) = {
    val t = reader.pull
    val tag = t match {
      case OpenObj(c, l) => OpenObj(c, l)
      case _ => throw new RuntimeException("Expected OpenObj token, got " + t)
    }
    val c = tag.clazz

    tag.link match {
      case LinkRef(id) => {
        val o = reader.retrieveCached(id)
        //Still need to close the tag - there is nothing inside the tag, since we are just a ref
        reader.pullAndAssert(CloseObj)
        o.asInstanceOf[Node]
      }
      case LinkId(id) => {

        val n = c.getConstructor(classOf[Txn]).newInstance(txn)
        reader.cache(id, n)
        
        //Fill out the node's Vars
        val accMap = Node.accessorsOfClass(c)
        while (reader.peek != CloseObj) {
          val t = reader.pull
          val accessorName = t match {
            case OpenField(n) => n
            case _ => throw new RuntimeException("Expected OpenField, got " + t)
          }
          val accessorValue = delegate.read(reader)
          accMap.get(accessorName) match {
            case None => {}
            case Some(m) => m.invoke(n).asInstanceOf[Box[Any]].update(accessorValue)
          }
        }
        reader.pullAndAssert(CloseObj)

        n.asInstanceOf[Node]
      }
      case LinkEmpty => throw new RuntimeException("A Node has neither ref nor id, which should not happen.")
    }


  }
  override def write(n : Node, writer: TokenWriter)(implicit txn: TxnR) = {

    //First, see if we are new
    writer.cache(n) match {
      //We were cached, just write out as a ref
      case Cached(ref) => {
        writer.write(OpenObj(n.getClass, LinkRef(ref)))
        writer.write(CloseObj)
      }

      //We are new, write out as normal, and include the id
      case New(id) => {
        writer.write(OpenObj(n.getClass, LinkId(id)))
        Node.accessors(n).foreach(entry => {
          writer.write(OpenField(entry._1))
          delegate.write(entry._2.invoke(n).asInstanceOf[Box[_]].apply, writer)
        })
        writer.write(CloseObj)
      }
    }

  }
}

object CaseClassCodec {
  val ctx = new Context {
    val name = "boxes"
  }
}

class CaseClassCodec(delegate:Codec[Any]) extends Codec[Product] {
  
  def genericGrater(c: Class[_]) = grater[AnyRef](CaseClassCodec.ctx, Manifest.classType(c))

  override def read(reader: TokenReader)(implicit txn: Txn) = {
    val t = reader.pull
    val tag = t match {
      case OpenObj(c, l) => OpenObj(c, l)
      case _ => throw new RuntimeException("Expected OpenObj token, got " + t)
    }
    val c = tag.clazz

    tag.link match {
      case LinkEmpty => {

        //Retrieve fields into a map
        val builder = Map.newBuilder[String, Any]
        while (reader.peek != CloseObj) {
          val t = reader.pull
          val fieldName = t match {
            case OpenField(n) => n
            case _ => throw new RuntimeException("Expected OpenField, got " + t)
          }
          val fieldValue = delegate.read(reader)
          builder += fieldName -> fieldValue
        }
        reader.pullAndAssert(CloseObj)

        genericGrater(c).fromMap(builder.result().toMap).asInstanceOf[Product]
      }
      case _ => throw new RuntimeException("A case class has a non-empty link, which should not happen.")
    }


  }
  override def write(n : Product, writer: TokenWriter)(implicit txn: TxnR) = {

    writer.write(OpenObj(n.getClass, LinkEmpty))
    n match {
      case nar: AnyRef => {
        val map = genericGrater(n.getClass()).toMap(n.asInstanceOf[AnyRef])
        
        map.foreach(entry => {
          writer.write(OpenField(entry._1))
          delegate.write(entry._2, writer)
        })
        writer.write(CloseObj)

      }
      //TODO this could be caught earlier, by allowing codecs to filter, CodecyByClass could then try less specific codecs, etc.
      case _ => throw new RuntimeException("A case class is not AnyRef, cannot serialise.")
    }

  }
}

//TODO share code, via implicits? Make exception print the token we got instead of expected one.
object ValCodecs {
  implicit object BooleanCodec extends CodecWithClass[Boolean] {
    override def clazz = classOf[java.lang.Boolean]
    override def write(t : Boolean, writer: TokenWriter)(implicit txn: TxnR) = writer.write(BooleanToken(t))
    override def read(reader: TokenReader)(implicit txn: Txn) = reader.pull match {
      case BooleanToken(t) => t
      case _ => throw new RuntimeException("Expected Boolean token")
    }
  }
  implicit object IntCodec extends CodecWithClass[Int] {
    override def clazz = classOf[java.lang.Integer]
    override def write(t : Int, writer: TokenWriter)(implicit txn: TxnR) = writer.write(IntToken(t))
    override def read(reader: TokenReader)(implicit txn: Txn) = reader.pull match {
      case IntToken(t) => t
      case _ => throw new RuntimeException("Expected Int token")
    }
  }
  implicit object LongCodec extends CodecWithClass[Long] {
    override def clazz = classOf[java.lang.Long]
    override def write(t : Long, writer: TokenWriter)(implicit txn: TxnR) = writer.write(LongToken(t))
    override def read(reader: TokenReader)(implicit txn: Txn) = reader.pull match {
      case LongToken(t) => t
      case _ => throw new RuntimeException("Expected Long token")
    }
  }
  implicit object FloatCodec extends CodecWithClass[Float] {
    override def clazz = classOf[java.lang.Float]
    override def write(t : Float, writer: TokenWriter)(implicit txn: TxnR) = writer.write(FloatToken(t))
    override def read(reader: TokenReader)(implicit txn: Txn) = reader.pull match {
      case FloatToken(t) => t
      case _ => throw new RuntimeException("Expected Float token")
    }
  }
  implicit object DoubleCodec extends CodecWithClass[Double] {
    override def clazz = classOf[java.lang.Double]
    override def write(t : Double, writer: TokenWriter)(implicit txn: TxnR) = writer.write(DoubleToken(t))
    override def read(reader: TokenReader)(implicit txn: Txn) = reader.pull match {
      case DoubleToken(t) => t
      case _ => throw new RuntimeException("Expected Double token")
    }
  }
  implicit object StringCodec extends CodecWithClass[String] {
    override def clazz = classOf[java.lang.String]
    override def write(t : String, writer: TokenWriter)(implicit txn: TxnR) = writer.write(StringToken(t))
    override def read(reader: TokenReader)(implicit txn: Txn) = reader.pull match {
      case StringToken(t) => t
      case _ => throw new RuntimeException("Expected String token")
    }
  }

}

//Unfortunately this bit doesn't work, since it requires a cycle. Will need a typeclass based system to fix this, one for later :( For
//now we have mutable, runtime stuff ;)
//
//trait CodecByClass extends Codec[Any] {
//  def added(codec:CodecWithClass[_]): CodecByClass = added(codec.clazz, codec)
//  def added(clazz:Class[_], codec:Codec[_]): CodecByClass
//  def get(clazz:Class[_]): Codec[_]
//}
//
//object ClassOrdering extends Ordering[Class[_]] {
//  override def compare(x: Class[_], y: Class[_]): Int = x.getCanonicalName().compareTo(y.getCanonicalName())
//}
//
//object CodecByClass {
//  val empty = new CodecByClassDefault(SortedMap.empty(ClassOrdering)): CodecByClass
//  val default = empty
//    .added(ValCodecs.IntCodec)
//    .added(ValCodecs.LongCodec)
//    .added(ValCodecs.FloatCodec)
//    .added(ValCodecs.DoubleCodec)
//    .added(ValCodecs.BooleanCodec)
//    .added(ValCodecs.StringCodec)
//    .added(new ListCodec(this), classOf[List[_]])
//    .added(new MapCodec(this), classOf[Map[_,_]])
//    .added(new SetCodec(this), classOf[Set[_]])
//    .added(new NodeCodec(this), classOf[Node])
//    .added(new CaseClassCodec(this), classOf[Product])
//    .added(new OptionCodec(this), classOf[Option[_]])
//    .added(new DateTimeCodec(this), classOf[DateTime])
//}
//
//
//private class CodecByClassDefault(codecs: SortedMap[Class[_], Codec[_]]) extends CodecByClass{
//
////  {
////    //Common default codecs
////    add(ValCodecs.IntCodec)
////    add(ValCodecs.LongCodec)
////    add(ValCodecs.FloatCodec)
////    add(ValCodecs.DoubleCodec)
////    add(ValCodecs.BooleanCodec)
////    add(ValCodecs.StringCodec)
////
////    add(new ListCodec(this), classOf[List[_]])
////    add(new MapCodec(this), classOf[Map[_,_]])
////    add(new SetCodec(this), classOf[Set[_]])
////    add(new NodeCodec(this), classOf[Node])
////    add(new CaseClassCodec(this), classOf[Product])
////    add(new OptionCodec(this), classOf[Option[_]])
////    add(new DateTimeCodec(this), classOf[DateTime])
////  }
//
//  def added(codec:CodecWithClass[_]): CodecByClass = added(codec.clazz, codec)
//
//  def added(clazz:Class[_], codec:Codec[_]): CodecByClass = {
//    //Don't add a codec for the same class twice
//    if (!codecs.keySet.contains(clazz)) {
//      new CodecByClassDefault(codecs.updated(clazz, codec))
//    } else {
//      throw new RuntimeException("There is already a codec " + codec + " for class " + clazz)
//    }
//  }
//
//  def get(clazz:Class[_]) = codecs.getOrElse(clazz, mostSpecific(clazz))
//
//  def mostSpecific(clazz: Class[_]) = {
//    //Starting from AnyCodec handling Any, scan through our codecs, preferring any that can handle the required clazz,
//    //and are a subclass of the current pair.
//    codecs.foldLeft[(Class[_], Codec[_])]((classOf[Any], AnyCodec))((acc, kv) => if (kv._1.isAssignableFrom(clazz) && acc._1.isAssignableFrom(kv._1)) kv else acc)._2
//  }
//  
//  //Need to look up class from tag, then use appropriate codec
//  override def read(reader : TokenReader)(implicit txn: Txn) = {
//    val t = reader.peek
//    val c = t match {
//      case OpenObj(clazz, _)           => clazz
//      case BooleanToken(p: Boolean)    => ValCodecs.BooleanCodec.clazz 
//      case IntToken(p: Int)            => ValCodecs.IntCodec.clazz 
//      case LongToken(p: Long)          => ValCodecs.LongCodec.clazz 
//      case FloatToken(p: Float)        => ValCodecs.FloatCodec.clazz 
//      case DoubleToken(p: Double)      => ValCodecs.DoubleCodec.clazz 
//      case StringToken(p: String)      => ValCodecs.StringCodec.clazz 
//      case OpenArr                      => ListCodec.clazz 
//
//      case _ => throw new RuntimeException("Expected OpenObj, OpenArr, or value Token got " + t)
//    }
//    
//    val codec = get(c)
//    codec.read(reader)
//  }
//
//  override def write(t : Any, writer: TokenWriter)(implicit txn: TxnR) = {
//    val tClass = t.asInstanceOf[AnyRef].getClass
//    get(tClass).asInstanceOf[Codec[Any]].write(t, writer)
//  }
//
//}



