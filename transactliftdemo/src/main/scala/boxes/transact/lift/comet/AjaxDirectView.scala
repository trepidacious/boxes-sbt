package boxes.transact.lift.comet

import boxes.transact.Box
import scala.xml.NodeSeq
import net.liftweb.http.js._
import boxes.transact.TxnR

object AjaxDirectView {
  def apply(content: NodeSeq): AjaxDirectView = new AjaxDirectView(content)
}

class AjaxDirectView(content: NodeSeq) extends AjaxView {
  override def render: NodeSeq = content
  override def partialUpdates: List[(TxnR)=>JsCmd] = List.empty 
}