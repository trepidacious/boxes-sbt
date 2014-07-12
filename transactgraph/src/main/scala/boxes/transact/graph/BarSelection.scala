package boxes.transact.graph

import boxes.graph.GraphMouseEventType._
import boxes.swing.SwingView
import java.awt.geom.{Line2D}
import java.awt.Color
import boxes.graph.Bar
import boxes.graph.GraphMouseEvent
import boxes.graph.BarChart
import boxes.graph.Vec2
import boxes.transact.Box
import boxes.transact.Shelf
import boxes.transact.BoxNow
import boxes.transact.TxnR
import boxes.graph.GraphCanvas
import boxes.transact.Txn
import boxes.graph.BarSelection
import boxes.graph.Area
import boxes.graph.GraphSpaces

object ColorBarByCatSelection {
  def apply[C1, C2, K](
      data: Box[Map[(C1, C2), Bar[K]]], 
      selection: Box[Set[(C1, C2)]], 
      barToUnselected: (Bar[K]=>Bar[K]) = 
        (bar: Bar[K]) => bar.copy(
          outline = bar.outline.map(_ => GraphSeries.barOutlineUnselectedColor), 
          fill = bar.fill.map(GraphSeries.blendColors(_, GraphSeries.unselectedColor, 0.8))
        )
      )(implicit shelf: Shelf) = 
    BoxNow.calc(implicit txn => {
      val d = data()
      val s = selection()
      d.map{case (cats, bar) => (cats, if (s.contains(cats)) bar else barToUnselected(bar))}
    })
}

//TODO deduplicate code in key and cat selection
object ColorBarByKeySelection {
  def apply[C1, C2, K](
      data: Box[Map[(C1, C2), Bar[K]]], 
      selection :Box[Set[K]], 
      barToUnselected: (Bar[K]=>Bar[K]) = 
        (bar: Bar[K]) => bar.copy(
          outline = bar.outline.map(_ => GraphSeries.barOutlineUnselectedColor), 
          fill = bar.fill.map(GraphSeries.blendColors(_, GraphSeries.unselectedColor, 0.8))
        )
      )(implicit shelf: Shelf) = 
    BoxNow.calc (implicit txn =>{
      val d = data()
      val s = selection()
      d.map{case (cats, bar) => (cats, if (s.contains(bar.key)) bar else barToUnselected(bar))}
    })
}

object GraphClickToSelectBarByCat{
  def apply[C1, C2, K](data: Box[Map[(C1, C2), Bar[K]]], selection: Box[Set[(C1, C2)]], barWidth: Box[Double], catPadding: Box[Double], 
    barPadding: Box[Double], enabled:Box[Boolean])(implicit shelf: Shelf, ord1: Ordering[C1], ord2: Ordering[C2]) = 
      new GraphClickToSelectBarByCat[C1, C2, K](data, selection, barWidth, catPadding, barPadding, enabled)
}

class GraphClickToSelectBarByCat[C1, C2, K](data: Box[Map[(C1, C2), Bar[K]]], selection: Box[Set[(C1, C2)]], barWidth: Box[Double], catPadding: Box[Double], 
    barPadding: Box[Double], enabled:Box[Boolean])(implicit shelf: Shelf, ord1: Ordering[C1], ord2: Ordering[C2]) extends UnboundedInputGraphLayer {

  override def onMouse(e: GraphMouseEvent)(implicit txn: Txn) = {
    if (enabled()) {
      e.eventType match {
        case CLICK => {
          val selectedSeries = BarSelection.selectedBar(data(), barWidth(), catPadding(), barPadding(), e)(ord1, ord2)
          selectedSeries.foreach((ss) => selection() = Set((ss._1, ss._2)))
          selectedSeries.isDefined
        }
        case _ => false
      }
    } else {
      false
    }
  }

}

object GraphSelectBarsByCatWithBox {
  def apply[C1, C2, K](data: Box[Map[(C1, C2), Bar[K]]], selection: Box[Set[(C1, C2)]], barWidth: Box[Double], catPadding: Box[Double], 
    barPadding: Box[Double], enabled:Box[Boolean], fill:Box[Color], outline:Box[Color])
    (implicit shelf: Shelf, ord1: Ordering[C1], ord2: Ordering[C2]) = {
      new GraphBox(fill, outline, enabled, new GraphBoxAction() {
        def apply(area: Area, spaces: GraphSpaces)(implicit txn: Txn) {
          val bs = BarSelection.selectedBar(data(), barWidth(), catPadding(), barPadding(), area);
          selection() = Set(bs.map(b => (b._1, b._2)): _*)  
        }
      })
    }
}


object GraphClickToSelectBarByKey{
  def apply[C1, C2, K](data: Box[Map[(C1, C2), Bar[K]]], selection: Box[Set[K]], barWidth: Box[Double], catPadding: Box[Double], 
    barPadding: Box[Double], enabled:Box[Boolean])(implicit shelf: Shelf, ord1: Ordering[C1], ord2: Ordering[C2]) = new GraphClickToSelectBarByKey[C1, C2, K](data, selection, barWidth, catPadding, 
    barPadding, enabled)
}

class GraphClickToSelectBarByKey[C1, C2, K](data: Box[Map[(C1, C2), Bar[K]]], selection: Box[Set[K]], barWidth: Box[Double], catPadding: Box[Double], 
    barPadding: Box[Double], enabled:Box[Boolean])(implicit shelf: Shelf, ord1: Ordering[C1], ord2: Ordering[C2]) extends GraphLayer {

  def paint(implicit txn: TxnR) = (canvas:GraphCanvas) => {}

  def onMouse(e:GraphMouseEvent)(implicit txn: Txn) = {
    if (enabled()) {
      e.eventType match {
        case CLICK => {
          val selectedSeries = BarSelection.selectedBar(data(), barWidth(), catPadding(), barPadding(), e)(ord1, ord2)
          selectedSeries.foreach((ss) => selection() = Set(ss._3.key))
          selectedSeries.isDefined
        }
        case _ => false
      }
    } else {
      false
    }
  }

  val dataBounds = BoxNow(None:Option[Area])
}

//TODO group the barWidth, catPadding and barPadding into one case class
object GraphSelectBarsByKeyWithBox {
  def apply[C1, C2, K](data: Box[Map[(C1, C2), Bar[K]]], selection: Box[Set[K]], barWidth: Box[Double], catPadding: Box[Double], barPadding: Box[Double], enabled: Box[Boolean], fill: Box[Color], outline: Box[Color])(implicit shelf: Shelf, ord1: Ordering[C1], ord2: Ordering[C2]) = {
    val a = new GraphBoxAction() {
      def apply(area: Area, spaces: GraphSpaces)(implicit txn: Txn) {
        val bs = BarSelection.selectedBar(data(), barWidth(), catPadding(), barPadding(), area);
        selection() = Set(bs.map(b => b._3.key): _*)
      }
    }
    new GraphBox(fill, outline, enabled, a)
  }
}