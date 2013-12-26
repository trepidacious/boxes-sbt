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


class AjaxStringView[T](label: Ref[NodeSeq], s: Ref[T], r:((T)=>NodeSeq)) extends AjaxView {
  lazy val id = net.liftweb.util.Helpers.nextFuncName
  def renderLabel = <span id={"label_" + id}>{label()}</span>
  //TODO Get text to line up with label
  def renderText = <span class="inline-string-view" id={"text_" + id}>{r(s())}</span>
  def render = <div id={id} class="form-horizontal">{(
                 (".controls [class+]" #> "controls-text") & 
                 ("#label" #> renderLabel) & 
                 ("#control" #> renderText)
               ).apply(AjaxView.formRowXML)}</div>
  override def partialUpdates = List(() => Replace("label_" + id, renderLabel) & Replace("text_" + id, renderText))
}

object AjaxStringView {
  def apply[T](label: Ref[NodeSeq], s: Ref[T], r:((T)=>NodeSeq) = {(t:T) => Text(t.toString): NodeSeq}): AjaxView = new AjaxStringView(label, s, r)
}

sealed trait PassViewMode

/**
 * Supports creating a password if we have none, or editing any existing password, repeatedly if required
 */
case object PassEditing extends PassViewMode

/**
 * Supports only creating a password, once the password is created a message is displayed stating this has been successful
 */
case object PassCreation extends PassViewMode

/**
 * Supports only resetting the password. The existing password is not required. Once the password is reset a message is
 * displayed stating this has been successful
 */
case object PassReset extends PassViewMode

class AjaxPassView(user: Ref[User], mode: PassViewMode, valid: (String) => Option[String]) extends AjaxView with Loggable {
  var oldPass: Option[String] = None
  var newPassA: Option[String] = None
  var newPassB: Option[String] = None
  
  lazy val id = "passview_" + net.liftweb.util.Helpers.nextFuncName

  //FIXME use resources for strings
  def formSubmit() {
    boxes.Box.transact{
      for {
        ph <- if (mode == PassEditing) user().passHash() else Some(PassHash(""))
        old <- if (mode == PassEditing) oldPass else Some("")
        a <- newPassA
        b <- newPassB
      } {
        if (a != b) {
          S.error("New password and repeat do not match.")
        } else if (mode==PassEditing && !ph.checkPass(old)) {
          S.error("Incorrect current password.")
        } else if (mode==PassCreation && !user().passHash().isEmpty) {
          S.error("Password is already set.")
        } else if (valid(a).isDefined) {
          S.error(valid(a).getOrElse("Invalid new password."))
        } else {
          user().passHash() = Some(PassHash(a))
          mode match {
            case PassCreation => S.notice("Password created.")
            case PassEditing => S.notice("Password changed.")
            case PassReset => S.notice("Password reset, please log in.")
          }
        }
      }      
    }
  }

  def passwordLine(label: String, commit: (String) => Unit) = {
    (
      ("#label" #> (label + ":")) & 
      ("#control" #> SHtml.password("", commit(_)))
    ).apply(AjaxView.formRowXML)
  }
  
  override def partialUpdates = List(() => Replace(id, render))
 
  def renderForm() = 
    AjaxView.formWithId(
      (if (mode == PassEditing) passwordLine("Current password", s=>oldPass=Some(s)) else Nil) ++
      passwordLine("New password",     s=>newPassA=Some(s)) ++
      passwordLine("Repeat password",  s=>newPassB=Some(s)) ++
      (
        ("#label" #> ("")) & 
        ("#control" #> ajaxSubmit("Change password", ()=>formSubmit(), "class" -> "btn btn-primary"))
      ).apply(AjaxView.formRowXML),
      id
    )
    
  def renderMessage(m: String) = <div id={id} class="form-horizontal">{(
                 (".controls [class+]" #> "controls-text") & 
                 ("#label" #> "") & 
                 ("#control" #> m)
               ).apply(AjaxView.formRowXML)}</div>
  
  def render = {
    mode match {
      case PassCreation => if (user().passHash().isDefined) renderMessage(S.?("user.password.set")) else renderForm()
      case PassEditing => renderForm()
      case PassReset => renderForm()  //TODO after the user has reset the password, we should really prevent further editing, and if possible log the user in at "/"
    }
  }
}

object AjaxPassView {
  
  val isAlphabetic = (s: String) => s.filter(c => c.isLetter || c.isWhitespace).length == s.length
  val isNumeric = (s: String) => s.filter(c => c.isDigit).length == s.length
  
  val minLength = 8
  val maxLength = 512
  val minLengthAlphabetic = 16
  val minLengthNumeric = 16
  
  //FIXME strings as resources
  def defaultValidator(s: String) = {
    if (s.length() < minLength) {
      Some("Password must be at least " + minLength + " characters")
    } else if (s.length() > maxLength) {
      Some("Password must be at most " + maxLength + " characters")
    //Note that we don't want to tell anyone looking over user's shoulder
    //that their password is alphabetic, but we also don't want to allow
    //short passes without numbers or punctuation
    } else if (s.length() < minLengthAlphabetic && isAlphabetic(s)) {
      Some("Password must be at least" + minLengthAlphabetic + " characters")
    //Just numbers is probably a bad idea too, unless you have lots of them
    } else if (s.length() < minLengthNumeric && isNumeric(s)) {
      Some("Password must be at least" + minLengthNumeric + " characters")
    } else {
      None
    }
  }
  
  def apply(user: Ref[User], mode: PassViewMode = PassEditing, valid: (String) => Option[String] = defaultValidator) = new AjaxPassView(user, mode, valid)
}

