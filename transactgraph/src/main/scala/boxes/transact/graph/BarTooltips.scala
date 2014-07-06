package boxes.transact.graph

import boxes.graph.GraphMouseEventType._
import boxes.swing.SwingView
import java.awt.geom.{Line2D}
import java.text.DecimalFormat
import boxes.graph.Vec2
import boxes.graph.GraphCanvas
import boxes.graph.Bar
import boxes.transact.Box
import boxes.transact.Shelf
import boxes.graph.BarTooltipRenderer
import boxes.transact.BoxNow
import boxes.transact.TxnR
import boxes.graph.GraphMouseEvent
import boxes.transact.Txn
import boxes.graph.BarSelection
import boxes.graph.Area
import boxes.graph.StringBarTooltipRenderer

object BarTooltips {

  val format = new DecimalFormat("0.###")

  def printRange(bar: Bar[_]) = {
    val l = List(bar.rangeMin, bar.rangeMax).flatten.map(format.format _)
    if (l.isEmpty) {
      ""
    } else {
      "(" + l.mkString(" to ") + ")"
    }
  }
  
  def printValueAndRange(bar: Bar[_]) = format.format(bar.value) + " " + printRange(bar)
    
  def defaultPrint[C1, C2, K] = (cat1: C1, cat2: C2, bar: Bar[K]) => cat1.toString() + ", " + cat2.toString() + " = " + printValueAndRange(bar)
  
  def apply[C1, C2, K](enabled: Box[Boolean], 
    data: Box[Map[(C1, C2), Bar[K]]],
    barWidth: Box[Double], catPadding: Box[Double], 
    barPadding: Box[Double],
    renderer: BarTooltipRenderer[C1, C2, K])
    (implicit shelf: Shelf, ord1: Ordering[C1], ord2: Ordering[C2]) = new BarTooltips(enabled, data, barWidth, catPadding, barPadding, renderer)

  def string[C1, C2, K](enabled: Box[Boolean], 
    data: Box[Map[(C1, C2), Bar[K]]],
    barWidth: Box[Double], catPadding: Box[Double], 
    barPadding: Box[Double], print:((C1, C2, Bar[K])=>String) = BarTooltips.defaultPrint)
    (implicit shelf: Shelf, ord1: Ordering[C1], ord2: Ordering[C2]) = new BarTooltips(enabled, data, barWidth, catPadding, barPadding, new StringBarTooltipRenderer(print))
}

class BarTooltips[C1, C2, K](enabled:Box[Boolean], 
    data: Box[Map[(C1, C2), Bar[K]]],
    barWidth: Box[Double], catPadding: Box[Double], 
    barPadding: Box[Double],
    renderer: BarTooltipRenderer[C1, C2, K])
    (implicit shelf: Shelf, ord1: Ordering[C1], ord2: Ordering[C2]) extends GraphLayer {

  private val toPaint: Box[Option[(C1, C2, Bar[K], Vec2)]] = BoxNow(None)

  def paint(implicit txn: TxnR) = {
    val e = enabled()
    val tp = toPaint()
    (canvas:GraphCanvas) => {
      if (e) {
        tp.foreach(pair => renderer.paint(canvas, pair._1, pair._2, pair._3, pair._4))
      }
    }
  }

  def onMouse(e: GraphMouseEvent)(implicit txn: Txn) = {

    if (enabled()) {
      //If the mouse position is ner enough to a series to "select" it, show tooltip with that series, at current mouse pixel point
      toPaint() = e.eventType match {
        case MOVE => {
          val s = BarSelection.selectedBar(data(), barWidth(), catPadding(), barPadding(), e)
          s.map(cat => (cat._1, cat._2, cat._3, e.spaces.toPixel(e.dataPoint)))
        }
        case _ => None
      }
    }

    false
  }

  val dataBounds = BoxNow(None: Option[Area])

}