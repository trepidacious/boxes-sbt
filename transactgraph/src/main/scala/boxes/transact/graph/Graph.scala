package boxes.transact.graph

import boxes.transact._
import boxes.graph.GraphCanvas
import boxes.graph.GraphMouseEvent
import boxes.graph.Area
import boxes.graph.Axis
import boxes.graph.Series
import boxes.graph.Vec2
import boxes.graph.Borders
import boxes.swing.SwingView
import boxes.swing.icons.IconFactory
import java.awt.Color
import java.text.DecimalFormat

trait GraphLayer {
  //When called, reads Box state and returns a method that will draw this state to a canvas
  def paint():(GraphCanvas => Unit)
  //Handle an event, returns false to allow it to reach other layers, or true to consume it
  def onMouse(event:GraphMouseEvent):Boolean
  def dataBounds:Box[Option[Area]]
}

trait GraphDisplayLayer extends GraphLayer {
  def onMouse(event:GraphMouseEvent) = false
}

abstract class UnboundedGraphDisplayLayer(implicit shelf: Shelf) extends GraphDisplayLayer {
  val dataBounds = BoxNow(None:Option[Area])
}

class GraphSeries[K](series:Box[List[Series[K]]], shadow:Boolean = false)(implicit shelf: Shelf) extends GraphLayer {

  def paint() = {
    val currentSeries = series.now()
    (canvas:GraphCanvas) => {
      canvas.clipToData
      for (s <- currentSeries) {
        s.painter.paint(canvas, s, shadow)
      }
    }
  }

  val dataBounds = BoxNow.calc(implicit txn => {
    series().foldLeft(None:Option[Area]){(seriesArea, series) => series.curve.foldLeft(seriesArea){
      (area, v) => area match {
        case None => Some(Area(v, Vec2.zero))
        case Some(a) => Some(a.extendToContain(v))
      }
    }}
  })

  def onMouse(event:GraphMouseEvent) = false

}

trait Graph {
  def layers: Box[List[GraphLayer]]
  def overlayers: Box[List[GraphLayer]]
  def dataArea: Box[Area]
  def borders: Box[Borders]
  def highQuality: Box[Boolean]
}

class GraphBG(val bg:Color, val dataBG:Color)(implicit shelf: Shelf) extends UnboundedGraphDisplayLayer {
  def paint() = {
    (canvas:GraphCanvas) => {
      canvas.color = bg
      canvas.fillRect(canvas.spaces.componentArea.origin, canvas.spaces.componentArea.size)

      canvas.color = dataBG
      canvas.fillRect(canvas.spaces.pixelArea.origin, canvas.spaces.pixelArea.size)
    }
  }
}

class GraphOutline(implicit shelf: Shelf) extends UnboundedGraphDisplayLayer {
  def paint() = {
    (canvas:GraphCanvas) => {
      canvas.color = GraphAxis.axisColor
      canvas.drawRect(canvas.spaces.pixelArea.origin, canvas.spaces.pixelArea.size)
    }
  }
}

class GraphHighlight(implicit shelf: Shelf) extends UnboundedGraphDisplayLayer {
  def paint() = {
    (canvas:GraphCanvas) => {
      canvas.color = SwingView.alternateBackgroundColor.brighter
      canvas.drawRect(canvas.spaces.pixelArea.origin + Vec2(-1, 1), canvas.spaces.pixelArea.size + Vec2(1, -1))
    }
  }
}

object GraphBusy {
  val pencil = IconFactory.image("GraphPencil")
}

class GraphBusy(val alpha: Box[Double])(implicit shelf: Shelf) extends UnboundedGraphDisplayLayer {
  def paint() = {
    val a = alpha.now()
    (canvas:GraphCanvas) => {
      canvas.color = SwingView.transparentColor(SwingView.selectionColor, a)
      val pa = canvas.spaces.pixelArea
      if (a > 0.5) {
        canvas.image(GraphBusy.pencil, pa.origin + pa.size + Vec2(-43, 10))
      }
    }
  }
}

object GraphShadow {
  val topLeft = IconFactory.image("GraphShadowTopLeft")
  val top = IconFactory.image("GraphShadowTop")
  val left = IconFactory.image("GraphShadowLeft")
}

class GraphShadow(implicit shelf: Shelf) extends UnboundedGraphDisplayLayer {
  def paint() = {
    (canvas:GraphCanvas) => {
      canvas.clipToData
      val w = GraphShadow.topLeft.getWidth(null)
      val h = GraphShadow.topLeft.getHeight(null)

      val a = canvas.spaces.pixelArea
      val tl = a.origin + a.size.withX(0)
      val bl = a.origin
      val br = a.origin + a.size.withY(0)

      canvas.image(GraphShadow.top, tl, Vec2(a.size.x, h))
      canvas.image(GraphShadow.left, tl, Vec2(w, -a.size.y))
      canvas.image(GraphShadow.top, bl + Vec2(0, 2), Vec2(a.size.x, -h))
      canvas.image(GraphShadow.left, br + Vec2(2, 0), Vec2(-w, a.size.y))
    }
  }
}
//
//object GraphAxis {
//  val fontSize = 10
//  val titleFontSize = 12
//  val fontColor = SwingView.textColor
//  val axisColor = SwingView.dividingColor
//  val axisHighlightColor = SwingView.alternateBackgroundColor.brighter
//  val gridMajorColor = new Color(0f, 0f, 0f, 0.08f)
//  val gridMinorColor = new Color(0f, 0f, 0f, 0.03f)
//  val defaultFormat = new DecimalFormat("0.###")
//
//  def apply(axis:Axis, pixelsPerMajor:Int = 100, format:DecimalFormat = GraphAxis.defaultFormat) = new GraphAxis(axis, pixelsPerMajor, format)
//}
//
//class GraphAxis(val axis:Axis, val pixelsPerMajor:Int = 100, val format:DecimalFormat = GraphAxis.defaultFormat, val gridlines: Boolean = true) extends UnboundedGraphDisplayLayer {
//
//  def paint() = {
//    (canvas:GraphCanvas) => {
//      val dataArea = canvas.spaces.dataArea
//
//      val ticks = Ticks(dataArea.axisBounds(axis), canvas.spaces.pixelArea.axisSize(axis), pixelsPerMajor)
//
//      ticks.foreach(t => {
//        val (p, major) = t
//        val start = canvas.spaces.toPixel(dataArea.axisPosition(axis, p))
//
//        canvas.color = GraphAxis.axisColor
//        axis match {
//          case X => canvas.line(start, start + Vec2(0, if (major) 8 else 4))
//          case Y => canvas.line(start, start + Vec2(if (major) -8 else -4, 0))
//        }
//        canvas.color = GraphAxis.axisHighlightColor
//        axis match {
//          case X => canvas.line(start + Vec2(1, 0), start + Vec2(1, if (major) 8 else 4))
//          case Y => canvas.line(start + Vec2(0, 1), start + Vec2(if (major) -8 else -4, 1))
//        }
//
//
//        if (major) {
//          canvas.color = GraphAxis.fontColor
//          canvas.fontSize = GraphAxis.fontSize
//          axis match {
//            case X => canvas.string(format.format(p), start + Vec2(0, 10), Vec2(0.5, 1))
//            case Y => canvas.string(format.format(p), start + Vec2(-10, 0), Vec2(1, 0.5))
//          }
//          if (gridlines) {
//            canvas.color = GraphAxis.gridMajorColor
//            canvas.line(start, start + canvas.spaces.pixelArea.axisPerpVec2(axis))
//          }
//        }
////        } else {
////          canvas.color = GraphAxis.gridMinorColor
////          canvas.line(start, start + canvas.spaces.pixelArea.axisPerpVec2(axis))
////        }
//      })
//    }
//  }
//}
//
//class GraphAxisTitle(val axis:Axis, name:Box[String, _]) extends UnboundedGraphDisplayLayer {
//  def paint() = {
//    val currentName = name()
//
//    (canvas:GraphCanvas) => {
//      val a = canvas.spaces.pixelArea
//      val tl = a.origin + a.size.withX(0)
//      val br = a.origin + a.size.withY(0)
//
//      canvas.color = GraphAxis.fontColor
//      canvas.fontSize = GraphAxis.titleFontSize
//      axis match {
//        case X => canvas.string(currentName, br + Vec2(-10, 28), Vec2(1, 1))
//        case Y => canvas.string(currentName, tl + Vec2(-52, 10 ), Vec2(1, 0), -1)
//      }
//    }
//  }
//}
//
//object GraphSelectBox {
//
//  def curvePointInArea(curve:List[Vec2], area:Area) = {
//    curve.foldLeft(false) {
//      (contains, p) => contains || area.contains(p)
//    }
//  }
//
//  def curveIntersectsArea(curve:List[Vec2], area:Area) = {
//    val rect = new Rectangle2D.Double(area.origin.x, area.origin.y, area.size.x, area.size.y)
//
//    //TODO we should finish this early if possible - there is some way to do this
//    val result = curve.foldLeft((false, None:Option[Vec2])){
//      (result, current) => {
//        val intersects = result._1
//        val previous = result._2
//        if (intersects) {
//          (intersects, Some(current))
//        } else {
//          previous match {
//            case None => (false, Some(current))
//            case Some(p) => {
//              if (rect.intersectsLine(p.x, p.y, current.x, current.y)) {
//                (true, Some(current))
//              } else {
//                (false, Some(current))
//              }
//            }
//          }
//        }
//      }
//    }
//
//    result._1
//  }
//
//  def seriesSelected(series:Series[_], area:Area) = {
//    if (series.painter.linesDrawn(series)) {
//      curveIntersectsArea(series.curve, area)
//    } else {
//      curvePointInArea(series.curve, area)
//    }
//  }
//
//
//  def apply[K](series:Box[List[Series[K]], _], fill:Box[Color, _], outline:Box[Color, _], selectionOut:VarBox[Set[K], _], enabled:Box[Boolean, _] = Val(true)) = {
//    new GraphBox(fill, outline, enabled, (area:Area, spaces:GraphSpaces) => {
//      val areaN = area.normalise
//      val selected = series().collect{
//        case s if (seriesSelected(s, areaN)) => s.key
//      }
//      selectionOut() = selected.toSet
//    })
//  }
//}
//
//object GraphZoomBox {
//  def apply(fill:Box[Color, _], outline:Box[Color, _], areaOut:VarBox[Option[Area], _], enabled:Box[Boolean, _] = Val(true)) = {
//    new GraphBox(fill, outline, enabled, (zoomArea:Area, spaces:GraphSpaces) => {
//      //Zoom out for second quadrant drag (x negative, y positive)
//      if (zoomArea.size.x < 0 && zoomArea.size.y > 0) {
//        areaOut() = None
//      } else {
//        areaOut() = Some(zoomArea.normalise)
//      }
//    })
//  }
//}
//
//object GraphGrab{
//  def apply(enabled:Box[Boolean, _] = Val(true), manualDataArea:VarBox[Option[Area], _], displayedDataArea:Box[Area, _]) = new GraphGrab(enabled, manualDataArea, displayedDataArea)
//}
//
//class GraphGrab(enabled:Box[Boolean, _] = Val(true), manualDataArea:VarBox[Option[Area], _], displayedDataArea:Box[Area, _]) extends GraphLayer {
//
//  private var maybeInitial:Option[GraphMouseEvent] = None
//
//  def paint() = (canvas:GraphCanvas) => {}
//
//  def onMouse(current:GraphMouseEvent) = {
//    if (enabled()) {
//      Box.transact{
//        current.eventType match {
//          case PRESS => {
//            maybeInitial = Some(current)
//            true
//          }
//          case DRAG => {
//            maybeInitial.foreach(initial => {
//              //If there is no manual zoom area, set it to the displayed area
//              if (manualDataArea() == None) {
//                manualDataArea() = Some(displayedDataArea())
//              }
//              manualDataArea().foreach(a => {
//                val initialArea = initial.spaces.dataArea
//                val currentPixelOnInitialArea = initial.spaces.toData(current.spaces.toPixel(current.dataPoint))
//
//                val dataDrag = initial.dataPoint - currentPixelOnInitialArea
//                manualDataArea() = Some(Area(initialArea.origin + dataDrag, initialArea.size))
//              })
//            })
//            true
//          }
//          case RELEASE => {
//            maybeInitial = None
//            true
//          }
//          case _ => false
//        }
//      }
//    } else {
//      false
//    }
//  }
//
//  val dataBounds = Val(None:Option[Area])
//
//}