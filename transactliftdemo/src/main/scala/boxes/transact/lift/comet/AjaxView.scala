package boxes.transact.lift.comet

import net.liftweb._
import http._
import SHtml._
import net.liftweb.common._
import net.liftweb.common.Box._
import net.liftweb.util._
import net.liftweb.actor._
import net.liftweb.util.Helpers._
import net.liftweb.http.js.JsCmds.{SetHtml}
import scala.xml.Text
import scala.xml.NodeSeq
import com.mongodb.casbah.commons.Imports._
import net.liftweb.http.js.JsCmds._
import net.liftweb.common.Loggable
import net.liftweb.http.js.JsCmd
import net.liftweb.http.js.JE
import scala.language.implicitConversions
import net.liftweb.http.js.JsCmds
import org.joda.time.LocalTime
import net.liftweb.http.js._
import net.liftweb.http.js.JsCmd._
import net.liftweb.http.js.JsCmds._
import net.liftweb.json.DefaultFormats
import net.liftweb.json._
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import scala.util.Try
import boxes.transact._

case object BoxesDateFormat{
   val formatter = ISODateTimeFormat.dateTime()  
}

//Parse any date string that can be handled by DateTime, and format DateTimes as ISO8601 date and time with millis
case object BoxesJodaDateTimeSerializer extends CustomSerializer[DateTime](format => (
  {
    case JString(s) => Try(new DateTime(s)).getOrElse(throw new MappingException("Invalid date format " + s))
    case JNull => null
  },
  {
    case d: DateTime => JString(BoxesDateFormat.formatter.print(d))
  }
))

trait AjaxView {
  def renderHeader: NodeSeq = NodeSeq.Empty
  def render: NodeSeq
  def partialUpdates: List[(TxnR)=>JsCmd] = List.empty 
}

object AjaxDataSourceView {
  def apply[T<:AnyRef](elementId: String, v: String, data: BoxR[T]) = new AjaxDataSourceView[T](elementId, v, data)
  def option[T<:AnyRef](elementId: String, v: String, data: BoxR[Option[T]]) = new AjaxOptionalDataSourceView[T](elementId, v, data)
}

class AjaxDataSourceView[T<:AnyRef](elementId: String, v: String, data: BoxR[T]) extends AjaxView with Loggable {
  implicit val formats = DefaultFormats + BoxesJodaDateTimeSerializer

  def render = NodeSeq.Empty
  
  override def partialUpdates = List(
      {implicit txn: TxnR => {
        val json = Serialization.write(data())
//        logger.info("Sending " + v + "=" + json)
        JE.JsRaw("angular.element('#" + elementId + "').scope().$apply(function ($scope) {$scope." + v + " = " + json + ";});")
      }}
  )
}

class AjaxOptionalDataSourceView[T<:AnyRef](elementId: String, v: String, data: BoxR[Option[T]]) extends AjaxView with Loggable {
  implicit val formats = DefaultFormats + BoxesJodaDateTimeSerializer

  def render = NodeSeq.Empty
  
  override def partialUpdates = List(
      {implicit txn: TxnR => {
        val json = Serialization.write(data().getOrElse(null))
//        logger.info("Sending " + v + "=" + json)
        JE.JsRaw("angular.element('#" + elementId + "').scope().$apply(function ($scope) {$scope." + v + " = " + json + ";});")
      }}
  )
}

object AjaxListOfViews {
  def apply(views: List[AjaxView]) = new AjaxListOfViews(views)
  def apply(views: AjaxView*) = new AjaxListOfViews(views.toList)
}

class AjaxListOfViews(views: List[AjaxView]) extends AjaxView {
  override def renderHeader = views.flatMap(_.renderHeader)
  def render = views.flatMap(_.render)
  override val partialUpdates = views.flatMap(_.partialUpdates)
}

/*
object AjaxListDataSourceView {
  def apply[T](elementId: String, v: String, list: Ref[List[T]], renderElement: (T)=>Map[String, String], deleteElement: (T)=>Unit) = new AjaxListDataSourceView(elementId, v, list, renderElement, deleteElement)
}

class AjaxListDataSourceView[T](elementId: String, v: String, list: Ref[List[T]], renderElement: (T)=>Map[String, String], deleteElement: (T)=>Unit) extends AjaxView with Loggable {
  lazy val id = net.liftweb.util.Helpers.nextFuncName

  def render = AjaxView.form(<span id={"list_data_source_" + id}></span>)
  
  def data() = {
    val l = list()
    val lines = l.map(t =>{
      val deleteAC = SHtml.ajaxCall(JE.JsRaw("1"), (s:String)=>{
        deleteElement(t)
        //logger.info("Called delete on " + t + " with string '" + s + "'")        
        })
      val r = renderElement(t)
      "{" + r.map{case (field, value) => "'" + field + "': " + value}.mkString(", ") + ", 'deleteGUID': '" + deleteAC.guid + "'}"
    })
    "[" + lines.mkString(", ") + "]"
  }
  
  override def partialUpdates = List(
//      () => JE.JsRaw("angular.element('#" + elementId + "').scope().$apply(function () {angular.element('#" + elementId + "').scope()." + v + " = " + data() + ";});")
      () => {
        logger.info("Sending " + data())
        JE.JsRaw("angular.element('#" + elementId + "').scope().$apply(function ($scope) {$scope." + v + " = " + data() + ";});")
      }
//      ,() => JE.JsRaw("alert(\"" + data() + "\")")
  )}


object AjaxNodeSeqView {
  def apply(label: Ref[NodeSeq] = Val(Text("")), control: Ref[NodeSeq] = Val(Text("")), error: Ref[NodeSeq] = Val(Text("")), addP: Boolean = true): AjaxNodeSeqView = 
    new AjaxNodeSeqView(label, control, error, addP)
}

class AjaxNodeSeqView(label: Ref[NodeSeq], control: Ref[NodeSeq], error: Ref[NodeSeq], addP: Boolean) extends AjaxView {
  lazy val id = net.liftweb.util.Helpers.nextFuncName
  
  def renderLabel = <span id={"label_" + id}>{label()}</span>
  def renderControl = if (addP) {
      <span id={"control_" + id}><p class="form-control-static">{control()}</p></span>
    } else{
      <span id={"control_" + id}>{control()}</span>    
    }
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

object AjaxDirectView {
  def apply(content: Ref[NodeSeq] = Val(Text(""))): AjaxDirectView = new AjaxDirectView(content)
}

class AjaxDirectView(content: Ref[NodeSeq]) extends AjaxView {
  lazy val id = net.liftweb.util.Helpers.nextFuncName
  
  def renderContent = <span id={"content_" + id}>{content()}</span>    

  def render = AjaxView.form(renderContent)
               
  override def partialUpdates = List(() => Replace("content_" + id, renderContent))
}

object AjaxListOfViews {
  def apply(views: List[AjaxView]) = new AjaxListOfViews(views)
  def apply(views: AjaxView*) = new AjaxListOfViews(views.toList)
}

class AjaxListOfViews(views: List[AjaxView]) extends AjaxView {
  override def renderHeader = views.flatMap(_.renderHeader)
  def render = views.flatMap(_.render)
  override val partialUpdates = views.flatMap(_.partialUpdates)
}

object AjaxButtonGroup {
  def apply(views: List[AjaxButtonView]) = new AjaxButtonGroup(views)
  def apply(views: AjaxButtonView*) = new AjaxButtonGroup(views.toList)
}

class AjaxButtonGroup(views: List[AjaxButtonView]) extends AjaxView {
  def render =   
  <div class="btn-group">
    {views.flatMap(_.render)}
  </div>

  override val partialUpdates = views.flatMap(_.partialUpdates)
}

object AjaxButtonToolbar {
  def apply(views: List[AjaxButtonGroup]) = new AjaxButtonToolbar(views)
  def apply(views: AjaxButtonGroup*) = new AjaxButtonToolbar(views.toList)
}

class AjaxButtonToolbar(views: List[AjaxButtonGroup]) extends AjaxView {
  def render =   
    <div class="btn-toolbar" role="toolbar">
      {views.flatMap(_.render)}
    </div>

  override val partialUpdates = views.flatMap(_.partialUpdates)
}


object AjaxOffsetButtonGroup {
  def apply(views: List[AjaxButtonView]) = new AjaxOffsetButtonGroup(views)
}

class AjaxOffsetButtonGroup(views: List[AjaxButtonView]) extends AjaxView {
  def render =   
//    <div class="form-horizontal">
//      <div class="form-group">
//        <div class="col-sm-offset-2 col-sm-6">
//          <div class="btn-group">
//            {views.flatMap(_.render)}
//          </div>
//        </div>
//      </div>
//    </div>

  override val partialUpdates = views.flatMap(_.partialUpdates)
}

object AjaxLabelledView {
  def apply(labelView: AjaxView, mainView: AjaxView) = new AjaxLabelledView(labelView, mainView)
  def nodeSeq(label: Ref[NodeSeq], mainView: AjaxView) = new AjaxLabelledView(AjaxDirectView(label), mainView)
}

class AjaxLabelledView(labelView: AjaxView, mainView: AjaxView) extends AjaxView {
  override def renderHeader = List(labelView, mainView).flatMap(_.renderHeader)
  def render = <div class="form-horizontal">
                 {AjaxView.formRow(labelView.render, mainView.render)}
               </div>
  override val partialUpdates = List(labelView, mainView).flatMap(_.partialUpdates)
}

object AjaxStaticView {
  def apply(contents: NodeSeq) = new AjaxStaticView(contents)
}

class AjaxStaticView(contents: NodeSeq) extends AjaxView {
  def render = contents
  override val partialUpdates = Nil
}

object AjaxOffsetView {
  def apply(view: AjaxView) = new AjaxOffsetView(view)
}

class AjaxOffsetView(view: AjaxView) extends AjaxView {
  override def renderHeader = view.renderHeader
  def render =
    <div class="form-horizontal">
      <div class="form-group">
        <div class="col-sm-offset-2 col-sm-6">
          {view.render}
        </div>
      </div>
    </div>

  override val partialUpdates = view.partialUpdates
}

object AjaxTextView {
  def apply(label: Ref[NodeSeq], s: Var[String], additionalError: Ref[Option[String]] = Val(None)): AjaxView = new AjaxTransformedStringView(label, s, (s: String) => s, (s: String) => Full(s), additionalError, None)
}

object AjaxPasswordView {
  def apply(label: Ref[NodeSeq], s: Var[String], additionalError: Ref[Option[String]] = Val(None)): AjaxView = new AjaxTransformedStringView(
      label, s, 
      (s: String) => s, 
      Full(_),
      additionalError,
      None,
      ("type" -> ("password")))
}

object AjaxNumberView {
  def apply[N](label: Ref[NodeSeq], v: Var[N], additionalError: Ref[Option[String]] = Val(None))(implicit n:Numeric[N], nc:NumericClass[N]): AjaxView = {
    new AjaxTransformedStringView(label, v, (n: N) => nc.format(n), nc.parseOption(_) ?~ "Please enter a valid number.", additionalError, None)
  }
}

class AjaxTransformedStringView[T](label: Ref[NodeSeq], v: Var[T], toS: (T) => String, toT: (String) => net.liftweb.common.Box[T], additionalError: Ref[Option[String]], controlNodeSeq: Option[NodeSeq], attrs: net.liftweb.http.SHtml.ElemAttr*) extends AjaxView {
  lazy val id = net.liftweb.util.Helpers.nextFuncName

  val inputError: Var[Option[String]] = Var(None)
  
  //This reaction looks odd - it just makes sure that whenever v changes,
  //input error is reset to "". This means that if the user has made an erroneous
  //input, the error notice is cleared when another change to v makes it 
  //irrelevant.
  val clearInputErrorReaction = Reaction{v(); inputError() = None}
  
  val error = Cal{inputError() orElse additionalError()}
  
  def renderLabel = <span id={"label_" + id}>{label()}</span>
  def renderError = <span class="help-inline" id={"error_" + id}>{error().getOrElse("")}</span>
  
  def commit(s: String) = {
     toT(s) match {
       case Full(t) => {
         v() = t
         inputError() = None //Note we clear input error here in case new value of t is same as old, thus not triggering the clearErrorReaction.
       }
       case Failure(msg, _, _) => inputError()=Some(msg)
       case Empty => inputError()= Some(S.?("text.input.invalid"))
     } 
  }
  
  val attrList = attrs.toList:+("id" -> ("control_" + id):ElemAttr):+("class" -> "form-control":ElemAttr)
  
  def render = {
    val text = SHtml.textAjaxTest(
           toS(v()), 
           commit(_), 
           t => {commit(t); Noop}, 
           attrList:_*)
           
    val control = controlNodeSeq match {
      case Some(ns) => ("#control" #> text).apply(ns)
      case None => text 
    }
    AjaxView.form(
     (
       (".form-group [id]" #> ("control_group_" + id)) &
       ("label [for+]" #> ("control_" + id)) & 
       ("#label" #> renderLabel) & 
       ("#control" #> control) &
       ("#error" #> renderError)
     ).apply(AjaxView.formRowXML)
   )
  }
               
  private def errorClass = "form-group" + error().map(_ => " has-error").getOrElse("")
  
  override def partialUpdates = List(
      () => Replace("label_" + id, renderLabel),
      () => SetValById("control_" + id, toS(v())), 
      () => Replace("error_" + id, renderError) & 
            SetElemById(("control_group_" + id), JE.Str(errorClass), "className")
  )
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
      ("#label" #> (label())) & 
      ("#control" #> List(zero, others).flatten)
    ).apply(AjaxView.formRowXML)}</div>
  }
  
  override def partialUpdates = List(() => Replace(id, render))
}

object AjaxStarsView{
  def apply(label: Ref[String], v: Var[Int], max: Var[Int]) = new AjaxStarsView(label, v, max)
}

class AjaxRedirectView(url: Ref[Option[String]]) extends AjaxView {
//  lazy val id = "redirect_" + net.liftweb.util.Helpers.nextFuncName

  def render = Text("") 
//  <span id={id}>{url()}</span>
  //Replace(id, render) & 
  override def partialUpdates = List(() => url().map(JsCmds.RedirectTo(_)).getOrElse(Noop))
}

object AjaxRedirectView{
  def apply(url: Ref[Option[String]]) = new AjaxRedirectView(url)
}
*/

