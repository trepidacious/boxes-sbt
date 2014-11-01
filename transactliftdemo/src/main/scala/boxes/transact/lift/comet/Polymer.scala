package boxes.transact.lift.comet

import net.liftweb.json._

object Polymer {
  def server(selector: String, v: String, data: String): String = "document.querySelector('" + selector + "').server('" + v + "', " + data + ");"
  def serverSetActionGUID(selector: String, v: String, guid: String) = server(selector, v, "{'actionGUID':'" + guid + "'}")
  def server[A <: AnyRef](selector: String, v: String, a: A)(implicit formats: Formats): String = server(selector, v, Serialization.write(a))
}