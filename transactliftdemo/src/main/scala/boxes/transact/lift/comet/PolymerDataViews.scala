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

object PolymerDataLinkView {
  def apply[T](selector: String, v: String, data: Box[T])(implicit shelf: Shelf, mf: Manifest[T]): AjaxView = new PolymerTransformingDataLinkView[T, T](selector, v, data, identity, identity, new DefaultJsonTransformer)
    
  def optional[T](selector: String, v: String, data: Box[Option[T]])(implicit shelf: Shelf, mf: Manifest[T]): AjaxView = {
    def toJ[T](o: Option[T]) = o.getOrElse(null.asInstanceOf[T])
    def toT[T](t: T) = if (t == null) None else Some(t)
    new PolymerTransformingDataLinkView[Option[T], T](selector, v, data, toJ, toT, new DefaultJsonTransformer)
  }
}

object PolymerDataSourceView {
  def apply[T](selector: String, v: String, data: Box[T])(implicit shelf: Shelf, mf: Manifest[T]): AjaxView = new PolymerTransformingDataSourceView[T, T](selector, v, data, identity, new DefaultJsonTransformer)
    
  def optional[T](selector: String, v: String, data: Box[Option[T]])(implicit shelf: Shelf, mf: Manifest[T]): AjaxView = {
    def toJ[T](o: Option[T]) = o.getOrElse(null.asInstanceOf[T])
    def toT[T](t: T) = if (t == null) None else Some(t)
    new PolymerTransformingDataSourceView[Option[T], T](selector, v, data, toJ, new DefaultJsonTransformer)
  }
}


private class PolymerTransformingDataLinkView[T, J](selector: String, v: String, data: Box[T], toJ: (T)=>J, toT: (J)=>T, jt: JsonTransformer[J])(implicit shelf: Shelf, mft: Manifest[T], mfj: Manifest[J]) extends AjaxView with Loggable {
  
  val lock = Lock()
  
  private var clientChangesUpTo = None: Option[Long]

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
  override def renderHeader = <span boxes-data-link={v}></span>
  
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
        val valueJson = toJ(d)
        val vvg = VersionedValueAndGUID(valueJson, i, guid)
        val json = jt.toJson(vvg)
//        val js = "document.querySelector('" + selector + "')." + v + " = " + json + ";"
        val js = "document.querySelector('" + selector + "').dataFromServer('" + v + "', " + json + ");"
        logger.info("PolymerTransformingDataLinkView sending:\n" + js)
        JE.JsRaw(js)
      }
    }
  }})
}

private class PolymerTransformingDataSourceView[T, J](selector: String, v: String, data: BoxR[T], toJ: (T)=>J, jt: JsonTransformer[J])(implicit shelf: Shelf, mft: Manifest[T], mfj: Manifest[J]) extends AjaxView with Loggable {
  def render(txn: TxnR) = NodeSeq.Empty
  override def partialUpdates = List({implicit txn: TxnR => {
    val valueJson = toJ(data())
    val vv = VersionedValue(valueJson, data.index())
    val json = jt.toJson(vv)
//    val js = "document.querySelector('" + selector + "')." + v + " = " + json + ";"
    val js = "document.querySelector('" + selector + "').dataFromServer('" + v + "', " + json + ");"
    logger.info("PolymerTransformingDataSourceView sending:\n" + js)
    JE.JsRaw(js)
  }})
}
