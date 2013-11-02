package boxes.persistence.protobuf

import boxes.persistence._
import com.google.protobuf.CodedOutputStream
import java.io.OutputStream
import com.google.protobuf.CodedInputStream
import java.io.IOException
import java.io.InputStream

object ProtobufTokenWriter {
  val openObj         = 1;
  val closeObj        = 2;
  val booleanToken    = 3;
  val intToken        = 4;
  val longToken       = 5;
  val floatToken      = 6;
  val doubleToken     = 7;
  val stringToken     = 8;
  val openField       = 9;
  val openArr         = 10;
  val closeArr        = 11;
  val end             = 12;
  val linkRef         = 13;
  val linkId          = 14;
  val linkEmpty       = 15;
  
  def apply(os: OutputStream, aliases: ClassAliases) = 
    new ProtobufTokenWriter(CodedOutputStream.newInstance(os), aliases, () => {os.flush();os.close()})
}

object ProtobufTokenReader{
  def apply(is: InputStream, aliases:ClassAliases) =
    new ProtobufTokenReader(CodedInputStream.newInstance(is), aliases)
}


class ProtobufTokenReader(is: CodedInputStream, aliases:ClassAliases) extends TokenReader {
  
  private var nextToken: Option[Token] = None
  
  def peek = {
    nextToken.getOrElse{
      val t = pullToken()
      nextToken = Some(t)
      t
    }
  }
  
  def pull() = {
    val t = peek
    if (t != End) nextToken = None
    t
  }  

  private def pullToken() : Token = {
    is.readRawVarint32() match {
      case ProtobufTokenWriter.openObj => {
        val clazz = aliases.forAlias(is.readString())
        val link = is.readRawVarint32() match {
          case ProtobufTokenWriter.linkRef => LinkRef(is.readRawVarint32())
          case ProtobufTokenWriter.linkId => LinkId(is.readRawVarint32())
          case ProtobufTokenWriter.linkEmpty => LinkEmpty
          case _ => throw new IOException("Invalid link type")
        }
        OpenObj(clazz, link)
      }
      case ProtobufTokenWriter.closeObj => CloseObj
      case ProtobufTokenWriter.booleanToken => BooleanToken(is.readBool())
      case ProtobufTokenWriter.intToken => IntToken(is.readRawVarint32())
      case ProtobufTokenWriter.longToken => LongToken(is.readRawVarint64())
      case ProtobufTokenWriter.floatToken => FloatToken(is.readFloat())
      case ProtobufTokenWriter.doubleToken => DoubleToken(is.readDouble())
      case ProtobufTokenWriter.stringToken => StringToken(is.readString())
      case ProtobufTokenWriter.openField => OpenField(is.readString())
      case ProtobufTokenWriter.openArr => OpenArr
      case ProtobufTokenWriter.closeArr => CloseArr
      case ProtobufTokenWriter.end => End
      case _ => throw new IOException("Invalid token type")
    }
  }
}

class ProtobufTokenWriter(os: CodedOutputStream, aliases: ClassAliases, onEnd: =>Unit) extends TokenWriter {

  def write(t: Token) {
    import ProtobufTokenWriter._;
    t match {
      case OpenObj(clazz, link) => {        
        os.writeRawVarint32(openObj);
        os.writeStringNoTag(aliases.forClass(clazz))
        link match {
          case LinkRef(id) => {
            os.writeRawVarint32(linkRef)
            os.writeRawVarint32(id)
          }
          case LinkId(id) => {
            os.writeRawVarint32(linkId)
            os.writeRawVarint32(id)
          }
          case _ => os.writeRawVarint32(linkEmpty);
        }
      }
      case CloseObj => {
        os.writeRawVarint32(closeObj);
      }
  
      case BooleanToken(p) => {
        os.writeRawVarint32(booleanToken)
        os.writeBoolNoTag(p)
      }
      case IntToken(p) => {
        os.writeRawVarint32(intToken)
        os.writeRawVarint32(p)        
      } 
      case LongToken(p) => {
        os.writeRawVarint32(longToken)
        os.writeRawVarint64(p)
      }
      case FloatToken(p) => {
        os.writeRawVarint32(floatToken)
        os.writeFloatNoTag(p)
      }
      case DoubleToken(p) => {
        os.writeRawVarint32(doubleToken)
        os.writeDoubleNoTag(p)
      }
      case StringToken(p) => {
        os.writeRawVarint32(stringToken)
        os.writeStringNoTag(p)
      }
  
      case OpenField(name) => {
        os.writeRawVarint32(openField)
        os.writeStringNoTag(name)
      }
  
      case OpenArr => {
        os.writeRawVarint32(openArr)
      }
      case CloseArr => {
        os.writeRawVarint32(closeArr)
      }
  
      case End => {
        os.writeRawVarint32(end)
        close()
      }          
    }
  }
  
  override def close() {
    super.close()
    os.flush
    onEnd    
  }
  
}