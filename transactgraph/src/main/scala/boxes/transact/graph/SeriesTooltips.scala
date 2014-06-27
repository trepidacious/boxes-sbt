package boxes.transact.graph

import boxes.graph.GraphMouseEventType._
import boxes.swing.SwingView
import java.awt.geom.{Line2D}
import boxes.graph.GraphCanvas
import boxes.graph.Series
import boxes.graph.Vec2
import boxes.transact.TxnR
import boxes.transact.Box
import boxes.transact.Shelf
import boxes.transact.BoxNow
import boxes.graph.GraphMouseEvent
import boxes.transact.Txn
import boxes.graph.SeriesSelection
import boxes.graph.Area

trait SeriesTooltipRenderer[K] {
  def paint(canvas:GraphCanvas, series:Series[K], pixelPos:Vec2)(implicit txn: TxnR)
}

trait TooltipPrinter[K] {
  def print(k: K)(implicit txn: TxnR): String
}

class StringTooltipPrinter[K] extends TooltipPrinter[K] {
  def print(k: K)(implicit txn: TxnR) = k.toString()
}

class StringSeriesTooltipRenderer[K](printer: TooltipPrinter[K]) extends SeriesTooltipRenderer[K]{
  def paint(canvas: GraphCanvas, series: Series[K], pixelPos: Vec2)(implicit txn: TxnR) {
    val s = printer.print(series.key)
    canvas.drawTooltip(s, pixelPos)
  }
}

class HighlightSeriesTooltipRenderer[K] extends SeriesTooltipRenderer[K]{
  def paint(canvas:GraphCanvas, series:Series[K], pixelPos:Vec2)(implicit txn: TxnR) {
    canvas.clipToData()
    series.painter.paint(canvas, series.copy(width = series.width + 3))
    canvas.clipToAll()
  }
}

object SeriesTooltips {
  val maxRadius = 10

  def string[K](series:Box[List[Series[K]]], enabled:Box[Boolean], printer: TooltipPrinter[K])(implicit shelf: Shelf) = 
    new SeriesTooltips[K](enabled, series, new StringSeriesTooltipRenderer[K](printer))

  def highlight[K](series:Box[List[Series[K]]], enabled:Box[Boolean])(implicit shelf: Shelf) = 
    new SeriesTooltips[K](enabled, series, new HighlightSeriesTooltipRenderer[K]())
}

class SeriesTooltips[K](enabled:Box[Boolean], series:Box[List[Series[K]]], renderer:SeriesTooltipRenderer[K])(implicit shelf: Shelf) extends GraphLayer {

  private val toPaint: Box[Option[(Series[K], Vec2)]] = BoxNow(None)

  def paint(implicit txn: TxnR) = {
    val e = enabled()
    val tp = toPaint()
    (canvas:GraphCanvas) => if (e) tp.foreach(pair => renderer.paint(canvas, pair._1, pair._2))
  }

  def onMouse(e:GraphMouseEvent)(implicit txn: Txn) = {

    if (enabled()) {
      //If the mouse position is near enough to a series to "select" it, show tooltip with that series, at current mouse pixel point
      toPaint() = e.eventType match {
        case MOVE => SeriesSelection.selectedSeries(series(), e).map((_, e.spaces.toPixel(e.dataPoint)))  //Stick the pixel point in with selected series
        case _ => None
      }
    }

    false
  }

  val dataBounds = BoxNow(None:Option[Area])

}