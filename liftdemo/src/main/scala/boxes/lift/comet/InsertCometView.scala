package boxes.lift.comet

import scala.xml.NodeSeq
import net.liftweb.http._
import net.liftweb.common._
import net.liftweb.util._
import Helpers._

abstract class InsertCometView[T](t: =>T)  {
  
  //Each CometActor has a unique name, so get a new actor per page reload
  lazy val name = net.liftweb.util.Helpers.nextFuncName

  def makeView(t: T): AjaxView
  
  final def render = {
    for (sess <- S.session) sess.sendCometActorMessage(
      "CometView", Full(name), makeView(t)
    )
    "* *" #> <lift:comet type={"CometView"} name={name}></lift:comet>
  }
}