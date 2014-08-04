package boxes.transact.lift.comet

import scala.language.implicitConversions
import scala.util.Try
import boxes.transact._
import net.liftweb.common.Loggable
import net.liftweb.http.SHtml
import net.liftweb.http.js.JE
import net.liftweb.http.js.JsCmd._
import net.liftweb.http.js.JsCmds._
import net.liftweb.json.DefaultFormats
import net.liftweb.json._
import scala.xml.NodeSeq
import scala.util.Success
import scala.util.Failure
import org.joda.time.DateTime
import net.liftweb.http.js.JsCmds
import scala.concurrent.Lock
import java.util.concurrent.locks.ReentrantLock
import boxes.transact.util.Lock

case class VersionedValue[T](value: T, index: Long)
case class VersionedValueAndGUID[T](value: T, index: Long, guid: String)

trait JsonTransformer[T] {
  def toJson(t: VersionedValueAndGUID[T]): String
  def toJson(t: VersionedValue[T]): String
  def fromJson(s: String): Try[VersionedValue[T]]
}

class DefaultJsonTransformer[T](implicit m: Manifest[T]) extends JsonTransformer[T] {
  implicit val formats = BoxesFormats.formats
  def toJson(t: VersionedValueAndGUID[T]) =  Serialization.write(t)
  def toJson(t: VersionedValue[T]) =  Serialization.write(t)
  def fromJson(s: String) = Try(parse(s).extract[VersionedValue[T]])
}

object DefaultJsonTransformer{
  def apply[T] = new DefaultJsonTransformer
}

object AjaxDataLinkView {
  def apply[T](elementId: String, v: String, data: Box[T])(implicit shelf: Shelf, mf: Manifest[T]): AjaxView = new AjaxTransformingDataLinkView[T, T](elementId, v, data, identity, identity, new DefaultJsonTransformer)
    
  def optional[T](elementId: String, v: String, data: Box[Option[T]])(implicit shelf: Shelf, mf: Manifest[T]): AjaxView = {
    def toJ[T](o: Option[T]) = o.getOrElse(null.asInstanceOf[T])
    def toT[T](t: T) = if (t == null) None else Some(t)
    new AjaxTransformingDataLinkView[Option[T], T](elementId, v, data, toJ, toT, new DefaultJsonTransformer)
  }
}

object AjaxDataSourceView {
  def apply[T](elementId: String, v: String, data: Box[T])(implicit shelf: Shelf, mf: Manifest[T]): AjaxView = new AjaxTransformingDataSourceView[T, T](elementId, v, data, identity, new DefaultJsonTransformer)
    
  def optional[T](elementId: String, v: String, data: Box[Option[T]])(implicit shelf: Shelf, mf: Manifest[T]): AjaxView = {
    def toJ[T](o: Option[T]) = o.getOrElse(null.asInstanceOf[T])
    def toT[T](t: T) = if (t == null) None else Some(t)
    new AjaxTransformingDataSourceView[Option[T], T](elementId, v, data, toJ, new DefaultJsonTransformer)
  }
}

private class AjaxTransformingDataLinkView[T, J](elementId: String, v: String, data: Box[T], toJ: (T)=>J, toT: (J)=>T, jt: JsonTransformer[J])(implicit shelf: Shelf, mft: Manifest[T], mfj: Manifest[J]) extends AjaxView with Loggable {
  
  val lock = Lock()
  
  private var clientChangesUpTo = None: Option[Long]  //TODO this could probably just be a boolean, set to true on client changes, false on others in partialUpdates

  val call = SHtml.ajaxCall(JE.JsRaw("1"), (s:String)=>{
    logger.info("Received " + s)
    jt.fromJson(s) match {
      case Success(inJ) => {
        //Lock while we do the transaction, so that we can see what revision index we get back
        lock{
          val (committed, revision) = shelf.transactToRevision(implicit txn => {
            val in = VersionedValue(toT(inJ.value), inJ.index)
            if (in.value != null && (in.index == data.index || (clientChangesUpTo.getOrElse(data.index-1) >= data.index))) {
              data() = in.value
              true
            } else {
              false
            }
          })
          if (committed) clientChangesUpTo = Some(revision.index)
        }
        
        JsCmds.Noop
      }
      case Failure(e) => logger.info("Failed to parse '" + s + "'", e)
    }
  })
      
  val guid = call.guid
  
  //This creates the controller code in Angular on the browser, that will send commits back to us on the named variable
  override def renderHeader = <div boxes-data-link={v}></div>
  
  def render(txn: TxnR) = NodeSeq.Empty
  
  override def partialUpdates = List({implicit txn: TxnR => {
    
    val d = data()
    val i = data.index()
    
    //If the client has applied changes at least as recent as the revision we are viewing, then the client
    //is already up to date and nothing needs to be done. Otherwise, the client is NOT up to date, and should
    //be sent a new revision.
    //This allows for quick repeated commits from a client without interruption from updates sent to
    //the client to confirm the commits. Note that if we get a change to the data that does NOT come from the
    //client, the client will be interrupted and updated with that overriding value
    lock{
      if (i <= clientChangesUpTo.getOrElse(i-1)) {
        Noop
      } else {
        //Client is NOT up to date
        clientChangesUpTo = None
        val vvg = VersionedValueAndGUID(toJ(d), i, guid)
        val json = jt.toJson(vvg)
        logger.info("AjaxTransformingDataLinkView sending " + json)
        JE.JsRaw("angular.element('#" + elementId + "').scope().$apply(function ($scope) {$scope." + v + " = " + json + ";});")
      }
    }
  }})
}

private class AjaxTransformingDataSourceView[T, J](elementId: String, v: String, data: BoxR[T], toJ: (T)=>J, jt: JsonTransformer[J])(implicit shelf: Shelf, mft: Manifest[T], mfj: Manifest[J]) extends AjaxView with Loggable {
  def render(txn: TxnR) = NodeSeq.Empty
  override def partialUpdates = List({implicit txn: TxnR => {
    val vv = VersionedValue(toJ(data()), data.index())
    val json = jt.toJson(vv)
    logger.info("AjaxTransformingDataSourceView sending " + json)
    JE.JsRaw("angular.element('#" + elementId + "').scope().$apply(function ($scope) {$scope." + v + " = " + json + ";});")
  }})
}
