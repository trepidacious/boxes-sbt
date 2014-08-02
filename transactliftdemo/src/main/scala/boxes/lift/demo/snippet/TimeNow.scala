package boxes.lift.demo.snippet

import net.liftweb._
import util._
import Helpers._

object TimeNow {
  def render = "* *" #> now.toString
}