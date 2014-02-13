package boxes.lift.comet

import net.liftweb._
import http._
import SHtml._
import net.liftweb.common._
import net.liftweb.common.Box._
import net.liftweb.util._
import net.liftweb.actor._
import net.liftweb.util.Helpers._
import net.liftweb.http.js.JsCmds.{SetHtml}
import boxes.View
import scala.xml.Text
import boxes.Var
import boxes.Path
import boxes.Val
import boxes.Ref
import scala.xml.NodeSeq
import boxes.BoxImplicits._
import boxes.Cal
import boxes.util.NumericClass
import boxes.persistence.mongo.MongoBox
import boxes.persistence.ClassAliases
import com.mongodb.casbah.commons.Imports._
import boxes.list.ListRef
import boxes.lift.user.PassHash
import boxes.Box
import net.liftweb.http.js.JsCmds._
import net.liftweb.common.Loggable
import net.liftweb.http.js.JsCmd
import boxes.Reaction
import net.liftweb.http.js.JE
import scala.language.implicitConversions
import boxes.lift.user.User
import net.liftweb.http.js.JsCmds
import boxes.lift.comet.view.AjaxButtonView

object AjaxModalView {
  def apply(body: AjaxView, footer: AjaxView, title: String, id: String) = new AjaxModalView(body, footer, title, id)
}

class AjaxModalView(body: AjaxView, footer: AjaxView, title: String, id: String) extends AjaxView {
  def render =   
    <div class="modal fade" id={id} tabindex="-1" role="dialog" aria-labelledby={id + "Label"} aria-hidden="true">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
            <h4 class="modal-title" id={id + "Label"}>{title}</h4>
          </div>
          <div class="modal-body">
            {body.render}
          </div>
          <div class="modal-footer">
            {footer.render}
          </div>
        </div>
      </div>
    </div>
  override val partialUpdates = List(body, footer).flatMap(_.partialUpdates)
}
