package boxes.transact.graph

import java.awt.Color
import java.text.DecimalFormat
import scala.collection.immutable.SortedSet
import scala.collection._
import boxes.transact.Box
import boxes.graph.Bar
import boxes.graph.Axis
import boxes.transact.Shelf
import Axis._
import boxes.transact.TxnR
import boxes.graph.GraphCanvas
import boxes.graph.BarChart
import boxes.graph.Vec2
import boxes.transact.BoxNow
import boxes.graph.Area
import boxes.graph.GraphMouseEvent
import boxes.transact.Txn

class GraphBarAxis[C1, C2](data: Box[Map[(C1, C2), Bar[_]]], barWidth: Box[Double], catPadding: Box[Double], 
    barPadding: Box[Double], val axis:Axis, 
    cat1Print: (C1 => String) = (c: C1)=>c.toString, 
    cat2Print: (C2 => String) = (c: C2)=>c.toString)
    (implicit shelf: Shelf, ord1: Ordering[C1], ord2: Ordering[C2]) extends UnboundedGraphDisplayLayer {

  def paint(implicit txn: TxnR) = {
    val d = data()
    val bw = barWidth()
    val cp = catPadding()
    val bp = barPadding()
    
    (canvas:GraphCanvas) => {
      val dataArea = canvas.spaces.dataArea
      
      val ticks = BarChart.integerTicks(dataArea.axisBounds(axis))

      val layout = BarChart.layout(d, bw, cp, ord1, ord2)
      
      //Secondary category labels
      for (pos <- layout.positions; bar <- d.get(pos.cat1, pos.cat2)) {
        
        val p = pos.x + bw/2
        if (canvas.spaces.dataArea.axisContains(axis, p)) {
          val start = canvas.spaces.toPixel(dataArea.axisPosition(axis, p))
          val text = cat2Print(pos.cat2)
          canvas.color = GraphAxis.fontColor
          canvas.fontSize = GraphAxis.fontSize
          axis match {
            case X => canvas.string(text, start + Vec2(0, 10), Vec2(0.5, 1))
            case Y => canvas.string(text, start + Vec2(-10, 0), Vec2(1, 0.5))
          }
        }
      }

      //Now primary category labels, if there is more than one primary category
      for (cat1 <- layout.cat1Positions.keySet; pos <- layout.cat1Positions.get(cat1)) {
        
        val p = (pos.x + pos.y)/2
        if (canvas.spaces.dataArea.axisContains(axis, p)) {
          val start = canvas.spaces.toPixel(dataArea.axisPosition(axis, p))
          val text = cat1Print(cat1)
          canvas.color = GraphAxis.fontColor
          canvas.fontSize = GraphAxis.titleFontSize
          axis match {
            case X => canvas.string(text, start + Vec2(0, 28), Vec2(0.5, 1))
            case Y => canvas.string(text, start + Vec2(-28, 0), Vec2(1, 0.5))
          }
        }
      }
      
      ticks.foreach(p => {

        val start = canvas.spaces.toPixel(dataArea.axisPosition(axis, p))

        canvas.color = GraphAxis.axisColor
        axis match {
          case X => canvas.line(start, start + Vec2(0, 8))
          case Y => canvas.line(start, start + Vec2(-8, 0))
        }
        canvas.color = GraphAxis.axisHighlightColor
        axis match {
          case X => canvas.line(start + Vec2(1, 0), start + Vec2(1, 8))
          case Y => canvas.line(start + Vec2(0, 1), start + Vec2(-8, 1))
        }

      })
    }
  }
}

class GraphBars[C1, C2, K](
    data: Box[Map[(C1, C2), Bar[K]]],
    barWidth: Box[Double], catPadding: Box[Double], 
    barPadding: Box[Double],
    shadow: Boolean = false)
    (implicit shelf: Shelf, ord1: Ordering[C1], ord2: Ordering[C2]) extends GraphLayer {

  def paint(implicit txn: TxnR) = {
    val d = data()
    val bw = barWidth()
    val cp = catPadding()
    val bp = barPadding()
    (canvas:GraphCanvas) => {
      canvas.clipToData

      for (pos <- BarChart.layout(d, bw, cp, ord1, ord2).positions; bar <- d.get(pos.cat1, pos.cat2)) {
        bar.painter.paint(canvas, pos.x + bp/2, bw-bp, bar, shadow)
      }
      
    }
  }
  
  val dataBounds = BoxNow.calc(implicit txn => {
    val d = data();
    if (d.isEmpty) {
      None
    } else {
      //Convert Bars to Vec2 of min/max, then find the overall range min/max
      val range = d.values.map(_.interval).reduceLeft(_ intervalUnion _)
      val cat1s = SortedSet(d.keySet.map(_._1).toSeq:_*)(ord1)
      val barCount = d.size
      
      //We display with specified width per Bar. We pad on between each cat1 group with
      //an additional specified padding unit
      val width = (math.max(0, cat1s.size - 1)) * catPadding() + barCount * barWidth();

      Some(Area(Vec2(0, range.x), Vec2(width, range.y-range.x)))
    }
  })

  def onMouse(event:GraphMouseEvent)(implicit txn: Txn) = false
}
