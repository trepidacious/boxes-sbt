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

object AjaxViewImplicits {
  implicit def refStringToRefNodeSeq(s: Ref[String]) = Cal{Text(s()): NodeSeq}
  implicit def stringToRefNodeSeq(s: String) = Val(Text(s): NodeSeq)
}

object AjaxView {
  val formRowXML = <div class="control-group">
                  <label class="control-label"><span id="label"/></label>
                  <div class="controls">
                    <span id="control"/>
                    <span id="error" class="help-inline"/>
                  </div>
                </div>
    
  def formRow(l: NodeSeq, c: NodeSeq) = (
      ("#label" #> l) & 
      ("#control" #> c)
    ).apply(AjaxView.formRowXML)
    
  def form(body: NodeSeq) = (<lift:form class="ajaxview form-horizontal">{body}</lift:form>)    
  def formWithId(body: NodeSeq, id: String) = (<div id={id}><lift:form class="ajaxview form-horizontal">{body}</lift:form></div>)    
}

trait AjaxView {
  def render: NodeSeq
  def partialUpdates: List[()=>JsCmd] = List.empty 
}

object AjaxNodeSeqView {
  def apply(label: Ref[NodeSeq] = Val(Text("")), control: Ref[NodeSeq] = Val(Text("")), error: Ref[NodeSeq] = Val(Text(""))): AjaxNodeSeqView = 
    new AjaxNodeSeqView(label, control, error)
}

class AjaxNodeSeqView(label: Ref[NodeSeq], control: Ref[NodeSeq], error: Ref[NodeSeq]) extends AjaxView {
  lazy val id = net.liftweb.util.Helpers.nextFuncName
  
  def renderLabel = <span id={"label_" + id}>{label()}</span>
  def renderControl = <span id={"control_" + id}>{control()}</span>
  def renderError = <span id={"error_" + id}>{error()}</span>

  def render = AjaxView.form(
               (
                 ("#label" #> renderLabel) & 
                 ("#control" #> renderControl) &
                 ("#error" #> renderError)
               ).apply(AjaxView.formRowXML)
             )
               
  override def partialUpdates = List(
      () => Replace("label_" + id, renderLabel),
      () => Replace("control_" + id, renderControl), 
      () => Replace("error_" + id, renderError)
  )
}

object AjaxListOfViews {
  def apply(views: ListRef[AjaxView]) = new AjaxListOfViews(views)
}

class AjaxListOfViews(views: ListRef[AjaxView]) extends AjaxView {
  def render = views().flatMap(_.render)
  override val partialUpdates = views().flatMap(_.partialUpdates)
}

object AjaxTextView {
  def apply(label: Ref[NodeSeq], s: Var[String]): AjaxView = new AjaxTransformedStringView(label, s, (s: String) => s, (s: String) => Full(s))
}

object AjaxNumberView {
  def apply[N](label: Ref[NodeSeq], v: Var[N])(implicit n:Numeric[N], nc:NumericClass[N]): AjaxView = {
    new AjaxTransformedStringView(label, v, (n: N) => nc.format(n), nc.parseOption(_) ?~ "Please enter a valid number.")
  }
}

class AjaxTransformedStringView[T](label: Ref[NodeSeq], v: Var[T], toS: (T) => String, toT: (String) => net.liftweb.common.Box[T]) extends AjaxView {
  lazy val id = net.liftweb.util.Helpers.nextFuncName

  val error = Var("")
  
  //This reaction looks odd - it just makes sure that whenever v changes,
  //error is reset to "". This means that if the user has made an erroneous
  //input, the error notice is cleared when another change to v makes it 
  //irrelevant.
  val clearErrorReaction = Reaction{v(); error() = ""}
  
  def renderLabel = <span id={"label_" + id}>{label()}</span>
  def renderError = <span class="help-inline" id={"error_" + id}>{error()}</span>
  
  def commit(s: String) = {
     toT(s) match {
       case Full(t) => {
         v() = t
         error() = "" //Note we clear here in case new value of t is same as old, thus not triggering the clearErrorReaction.
       }
       case Failure(msg, _, _) => error.update(msg)
       case Empty => error.update("Invalid input.")
     } 
  }
  
  def render = AjaxView.form(
                 (
                   (".control-group [id]" #> ("control_group_" + id)) &
                   ("label [for+]" #> ("control_" + id)) & 
                   ("#label" #> renderLabel) & 
                   ("#control" #> SHtml.textAjaxTest(
                       toS(v()), 
                       commit(_), 
                       t => {commit(t); Noop}, 
                       "id" -> ("control_" + id))) &
                   ("#error" #> renderError)
                 ).apply(AjaxView.formRowXML)
               )
               
  private def errorClass = "control-group" + (if (error().length == 0) "" else " error")
  override def partialUpdates = List(
      () => Replace("label_" + id, renderLabel),
      () => SetValById("control_" + id, toS(v())), 
      () => Replace("error_" + id, renderError) & 
            SetElemById(("control_group_" + id), JE.Str(errorClass), "className")
  )
}

object AjaxButtonView {
  def apply(label: Ref[NodeSeq], enabled: Ref[Boolean], action: => Unit) = new AjaxButtonView(label, enabled, action)
}

class AjaxButtonView(label: Ref[NodeSeq], enabled: Ref[Boolean], action: => Unit) extends AjaxView {
  lazy val id = "button_" + net.liftweb.util.Helpers.nextFuncName
  
  def render = {
    <div id={id} class="form-horizontal">
      {ajaxButton(label(), () => {action; Noop}, "class" -> "btn")}
    </div>
  }

  override def partialUpdates = List(() => Replace(id, render))
}

class AjaxStarsView(label: Ref[String], v: Var[Int], max: Var[Int]) extends AjaxView {
  lazy val id = "stars_" + net.liftweb.util.Helpers.nextFuncName
  
  def starClass(i: Int) = "star star_" + (if(v() >= i) "selected" else "unselected") 
  def starDiv(i: Int) = <div class={starClass(i)}></div>
  
  def render = {
    val zero = a(() => v() = 0, <div class="star star_clear"></div>) //Set zero stars
    val others = Range(1, max()+1).map(i => {
      a(() => v() = i, starDiv(i))
    }).toList
    
    <div id={id} class="form-horizontal">{(
      ("#label" #> (label() + ":")) & 
      ("#control" #> List(zero, others).flatten)
    ).apply(AjaxView.formRowXML)}</div>
  }
  
  override def partialUpdates = List(() => Replace(id, render))
}

object AjaxStarsView{
  def apply(label: Ref[String], v: Var[Int], max: Var[Int]) = new AjaxStarsView(label, v, max)
}



