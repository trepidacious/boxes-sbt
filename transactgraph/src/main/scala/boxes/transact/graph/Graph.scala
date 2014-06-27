package boxes.transact.graph

import boxes.transact._
import boxes.graph.GraphCanvas
import boxes.graph.GraphMouseEvent
import boxes.graph.GraphMouseEventType
import boxes.graph.Area
import boxes.graph.Axis
import boxes.graph.Ticks
import boxes.graph.Series
import boxes.graph.Vec2
import boxes.graph.Borders
import boxes.graph.GraphSpaces
import boxes.swing.SwingView
import boxes.swing.icons.IconFactory
import java.awt.Color
import java.awt.geom.Rectangle2D
import java.text.DecimalFormat
import Axis._
import GraphMouseEventType._
import boxes.graph.GraphThreePartPainter
import boxes.graph.GraphThreePartPainterVertical
import boxes.graph.GraphZoomerAxis
import boxes.graph.SeriesSelection

trait GraphLayer {
  //When called, reads Box state and returns a method that will draw this state to a canvas
  def paint(implicit txn: TxnR):(GraphCanvas => Unit)
  //Handle an event, returns false to allow it to reach other layers, or true to consume it
  def onMouse(event:GraphMouseEvent)(implicit txn: Txn): Boolean
  def dataBounds:Box[Option[Area]]
}

trait GraphDisplayLayer extends GraphLayer {
  def onMouse(event:GraphMouseEvent)(implicit txn: Txn) = false
}

abstract class UnboundedGraphDisplayLayer(implicit shelf: Shelf) extends GraphDisplayLayer {
  val dataBounds = BoxNow(None:Option[Area])
}

object GraphSeries {
  val shadowColor = new Color(220, 220, 220)
  val shadowOffset = Vec2(1, 1)
  val barShadowColor = new Color(215, 215, 215)
  val barOutlineColor = SwingView.dividingColor.darker()
  val barOutlineUnselectedColor = SwingView.dividingColor
  val barShadowOffset = Vec2(3, 3)
  val unselectedColor = new Color(230, 230, 230)
  
  
  /**
   * Scale a {@link Color} by the same factor across red, green and blue,
   * then clip to 0-255 and return as a new {@link Color}
   * @param c     The input color
   * @param factor  The factor
   * @return      The output scaled color
   */
   def scaleColor(c: Color, factor: Double) = new Color( 
     clip((c.getRed() * factor).asInstanceOf[Int], 0, 255), 
     clip((c.getGreen() * factor).asInstanceOf[Int], 0, 255),
     clip((c.getBlue() * factor).asInstanceOf[Int], 0, 255)
   )
    
  /**
   * Scale a {@link Color} alpha value by a factor,
   * then clip to 0-255 and return as a new {@link Color}
   * @param c     The input color
   * @param factor  The factor
   * @return      The output scaled color
   */
   def transparentColor(c: Color, factor: Double) = new Color( 
     c.getRed(), 
     c.getGreen(),
     c.getBlue(),
     clip((c.getAlpha() * factor).asInstanceOf[Int], 0, 255)
   )
  
  /**
   * Fade a {@link Color} to white by the same factor across red, green and blue,
   * then clip to 0-255 and return as a new {@link Color}
   * @param c     The input color
   * @param factor  The factor
   * @return      The output faded color
   */
   def fadeColorToWhite(c: Color, factor: Double) = new Color( 
     clip((lerp(c.getRed(), 255, factor).asInstanceOf[Int]), 0, 255), 
     clip((lerp(c.getGreen(), 255, factor).asInstanceOf[Int]), 0, 255),
     clip((lerp(c.getBlue(), 255, factor).asInstanceOf[Int]), 0, 255)
   )
    
  /**
   * Blend from one {@link Color} to another
   * then clip to 0-255 and return as a new {@link Color}
   * @param first     The first input color
   * @param second      The second input color
   * @param factor  The factor - 0 gives first color, 1 gives second, values in between
   *          interpolate, values outside 0-1 extrapolate (but are clipped)
   * @return      The output scaled color
   */
   def blendColors(c1: Color, c2: Color, factor: Double) = new Color( 
     clip((lerp(c1.getRed(), c2.getRed(), factor).asInstanceOf[Int]), 0, 255), 
     clip((lerp(c1.getGreen(), c2.getGreen(), factor).asInstanceOf[Int]), 0, 255),
     clip((lerp(c1.getBlue(), c2.getBlue(), factor).asInstanceOf[Int]), 0, 255)
   )
 
    
  /**
   * Linearly interpolate/extrapolate from one double to another, by a certain
   * scale
   * @param from    The value returned when by == 0 
   * @param to    The value returned when by == 1
   * @param by    The interpolation/extrapolation position
   * @return      The lerped value
   */
  def lerp(from: Double, to: Double, by: Double) =  (from * (1-by)) + to * by
  
  /**
   * Return a double value clipped to lie from min to max, inclusive
   * @param value   The value
   * @param min   The minimum (inclusive)
   * @param max   The maximum (inclusive)
   * @return      The clipped value
   */
  def clip(value: Double, min: Double, max: Double) = if (value < min) min else if (value > max) max else value
  
  /**
   * Return an int value clipped to lie from min to max, inclusive
   * @param value   The value
   * @param min   The minimum (inclusive)
   * @param max   The maximum (inclusive)
   * @return      The clipped value
   */
  def clip(value: Int, min: Int, max: Int) = if (value < min) min else if (value > max) max else value
}

class GraphSeries[K](series:Box[List[Series[K]]], shadow:Boolean = false)(implicit shelf: Shelf) extends GraphLayer {

  def paint(implicit txn: TxnR) = {
    val currentSeries = series()
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

  def onMouse(event:GraphMouseEvent)(implicit txn: Txn) = false

}

trait Graph {
  def layers: Box[List[GraphLayer]]
  def overlayers: Box[List[GraphLayer]]
  def dataArea: Box[Area]
  def borders: Box[Borders]
  def highQuality: Box[Boolean]
}

class GraphBG(val bg:Color, val dataBG:Color)(implicit shelf: Shelf) extends UnboundedGraphDisplayLayer {
  def paint(implicit txn: TxnR) = {
    (canvas:GraphCanvas) => {
      canvas.color = bg
      canvas.fillRect(canvas.spaces.componentArea.origin, canvas.spaces.componentArea.size)

      canvas.color = dataBG
      canvas.fillRect(canvas.spaces.pixelArea.origin, canvas.spaces.pixelArea.size)
    }
  }
}

class GraphOutline(implicit shelf: Shelf) extends UnboundedGraphDisplayLayer {
  def paint(implicit txn: TxnR) = {
    (canvas:GraphCanvas) => {
      canvas.color = GraphAxis.axisColor
      canvas.drawRect(canvas.spaces.pixelArea.origin, canvas.spaces.pixelArea.size)
    }
  }
}

class GraphHighlight(implicit shelf: Shelf) extends UnboundedGraphDisplayLayer {
  def paint(implicit txn: TxnR) = {
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
  def paint(implicit txn: TxnR) = {
    val a = alpha()
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
  def paint(implicit txn: TxnR) = {
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

object GraphAxis {
  val fontSize = 10
  val titleFontSize = 12
  val fontColor = SwingView.textColor
  val axisColor = SwingView.dividingColor
  val axisHighlightColor = SwingView.alternateBackgroundColor.brighter
  val gridMajorColor = new Color(0f, 0f, 0f, 0.08f)
  val gridMinorColor = new Color(0f, 0f, 0f, 0.03f)
  val defaultFormat = new DecimalFormat("0.###")

  def apply(axis:Axis, pixelsPerMajor:Int = 100, format:DecimalFormat = GraphAxis.defaultFormat)(implicit shelf: Shelf) = new GraphAxis(axis, pixelsPerMajor, format)
}

class GraphAxis(val axis:Axis, val pixelsPerMajor:Int = 100, val format:DecimalFormat = GraphAxis.defaultFormat, val gridlines: Boolean = true)(implicit shelf: Shelf) extends UnboundedGraphDisplayLayer {

  def paint(implicit txn: TxnR) = {
    (canvas:GraphCanvas) => {
      val dataArea = canvas.spaces.dataArea

      val ticks = Ticks(dataArea.axisBounds(axis), canvas.spaces.pixelArea.axisSize(axis), pixelsPerMajor)

      ticks.foreach(t => {
        val (p, major) = t
        val start = canvas.spaces.toPixel(dataArea.axisPosition(axis, p))

        canvas.color = GraphAxis.axisColor
        axis match {
          case X => canvas.line(start, start + Vec2(0, if (major) 8 else 4))
          case Y => canvas.line(start, start + Vec2(if (major) -8 else -4, 0))
        }
        canvas.color = GraphAxis.axisHighlightColor
        axis match {
          case X => canvas.line(start + Vec2(1, 0), start + Vec2(1, if (major) 8 else 4))
          case Y => canvas.line(start + Vec2(0, 1), start + Vec2(if (major) -8 else -4, 1))
        }


        if (major) {
          canvas.color = GraphAxis.fontColor
          canvas.fontSize = GraphAxis.fontSize
          axis match {
            case X => canvas.string(format.format(p), start + Vec2(0, 10), Vec2(0.5, 1))
            case Y => canvas.string(format.format(p), start + Vec2(-10, 0), Vec2(1, 0.5))
          }
          if (gridlines) {
            canvas.color = GraphAxis.gridMajorColor
            canvas.line(start, start + canvas.spaces.pixelArea.axisPerpVec2(axis))
          }
        }
//        } else {
//          canvas.color = GraphAxis.gridMinorColor
//          canvas.line(start, start + canvas.spaces.pixelArea.axisPerpVec2(axis))
//        }
      })
    }
  }
}

class GraphAxisTitle(val axis:Axis, name:Box[String])(implicit shelf: Shelf) extends UnboundedGraphDisplayLayer {
  def paint(implicit txn: TxnR) = {
    val currentName = name()

    (canvas:GraphCanvas) => {
      val a = canvas.spaces.pixelArea
      val tl = a.origin + a.size.withX(0)
      val br = a.origin + a.size.withY(0)

      canvas.color = GraphAxis.fontColor
      canvas.fontSize = GraphAxis.titleFontSize
      axis match {
        case X => canvas.string(currentName, br + Vec2(-10, 28), Vec2(1, 1))
        case Y => canvas.string(currentName, tl + Vec2(-52, 10 ), Vec2(1, 0), -1)
      }
    }
  }
}

trait GraphBoxAction {
  def apply(area: Area, spaces: GraphSpaces)(implicit txn: Txn): Unit
}

class GraphBox(fill:Box[Color], outline:Box[Color], enabled:Box[Boolean], action: GraphBoxAction, val minSize:Int = 5, val axis: Option[Axis] = None)(implicit shelf: Shelf) extends GraphLayer {
  private val area: Box[Option[Area]] = BoxNow(None)

  def bigEnough(a:Area) = (math.abs(a.size.x) > minSize || math.abs(a.size.y) > minSize)

  def paint(implicit txn: TxnR) = {
    val cFill = fill()
    val cOutline = outline()
    val cEnabled = enabled()
    val cArea = area()

    (canvas:GraphCanvas) => {
      if (cEnabled) {
        cArea.foreach(a => {
          val pixelArea = canvas.spaces.toPixel(a)
          if (bigEnough(pixelArea)) {
            canvas.color = cFill
            canvas.fillRect(canvas.spaces.toPixel(a))
            canvas.color = cOutline
            canvas.drawRect(canvas.spaces.toPixel(a))
          }
        })
      }
    }
  }

  def onMouse(e:GraphMouseEvent)(implicit txn: Txn) = {
    if (enabled()) {
      e.eventType match {
        case PRESS => {
          area() = Some(Area(e.dataPoint, Vec2(0, 0)).replaceAxis(axis, e.spaces.dataArea))
          true
        }
        case DRAG => {
          area().foreach(a => {
            area() = Some(Area(a.origin, e.dataPoint - a.origin).replaceAxis(axis, e.spaces.dataArea))
          })
          true
        }
        
        case RELEASE => {
          area().foreach(a => {
            area() = None
            val dragArea = Area(a.origin, e.dataPoint - a.origin)
            val pixelDragArea = e.spaces.toPixel(dragArea)
            if (bigEnough(pixelDragArea)) {
              action.apply(dragArea.replaceAxis(axis, e.spaces.dataArea), e.spaces)
            }
          })
          true
        }
        case _ => false
      }
    } else {
      false
    }

  }

  val dataBounds = BoxNow(None:Option[Area])

}

object GraphSelectBox {

  def curvePointInArea(curve:List[Vec2], area:Area) = {
    curve.foldLeft(false) {
      (contains, p) => contains || area.contains(p)
    }
  }

  def curveIntersectsArea(curve:List[Vec2], area:Area) = {
    val rect = new Rectangle2D.Double(area.origin.x, area.origin.y, area.size.x, area.size.y)

    //TODO we should finish this early if possible - there is some way to do this
    val result = curve.foldLeft((false, None:Option[Vec2])){
      (result, current) => {
        val intersects = result._1
        val previous = result._2
        if (intersects) {
          (intersects, Some(current))
        } else {
          previous match {
            case None => (false, Some(current))
            case Some(p) => {
              if (rect.intersectsLine(p.x, p.y, current.x, current.y)) {
                (true, Some(current))
              } else {
                (false, Some(current))
              }
            }
          }
        }
      }
    }

    result._1
  }

  def seriesSelected(series:Series[_], area:Area) = {
    if (series.painter.linesDrawn(series)) {
      curveIntersectsArea(series.curve, area)
    } else {
      curvePointInArea(series.curve, area)
    }
  }


  def apply[K](series:Box[List[Series[K]]], fill:Box[Color], outline:Box[Color], selectionOut:Box[Set[K]], enabled:Box[Boolean])(implicit shelf: Shelf) = {
    
    val action = new GraphBoxAction {
      def apply(area: Area, spaces: GraphSpaces)(implicit txn: Txn) {
        val areaN = area.normalise
        val selected = series().collect{
          case s if (seriesSelected(s, areaN)) => s.key
        }
        selectionOut() = selected.toSet        
      }
    }
    
    new GraphBox(fill, outline, enabled, action)
  }
}

object GraphZoomBox {
  def apply(fill: Box[Color], outline: Box[Color], areaOut: Box[Option[Area]], enabled: Box[Boolean])(implicit shelf: Shelf) = {
    val action = new GraphBoxAction {
      def apply(area: Area, spaces: GraphSpaces)(implicit txn: Txn) {
        //Zoom out for second quadrant drag (x negative, y positive)
        if (area.size.x < 0 && area.size.y > 0) {
          areaOut() = None
        } else {
          areaOut() = Some(area.normalise)
        }
      }
    }
    
    new GraphBox(fill, outline, enabled, action)
  }
}


object GraphGrab{
  def apply(enabled: Box[Boolean], manualDataArea: Box[Option[Area]], displayedDataArea: Box[Area])(implicit shelf: Shelf) = new GraphGrab(enabled, manualDataArea, displayedDataArea)
}

class GraphGrab(enabled:Box[Boolean], manualDataArea:Box[Option[Area]], displayedDataArea:Box[Area])(implicit shelf: Shelf) extends GraphLayer {

  private val maybeInitial: Box[Option[GraphMouseEvent]] = BoxNow(None)

  def paint(implicit txn: TxnR) = (canvas:GraphCanvas) => {}

  def onMouse(current:GraphMouseEvent)(implicit txn: Txn) = {
    if (enabled()) {
      current.eventType match {
        case PRESS => {
          maybeInitial() = Some(current)
          true
        }
        case DRAG => {
          maybeInitial().foreach(initial => {
            //If there is no manual zoom area, set it to the displayed area
            if (manualDataArea() == None) {
              manualDataArea() = Some(displayedDataArea())
            }
            manualDataArea().foreach(a => {
              val initialArea = initial.spaces.dataArea
              val currentPixelOnInitialArea = initial.spaces.toData(current.spaces.toPixel(current.dataPoint))

              val dataDrag = initial.dataPoint - currentPixelOnInitialArea
              manualDataArea() = Some(Area(initialArea.origin + dataDrag, initialArea.size))
            })
          })
          true
        }
        case RELEASE => {
          maybeInitial() = None
          true
        }
        case _ => false
      }
    } else {
      false
    }
  }

  val dataBounds = BoxNow(None:Option[Area])

}

object AxisTooltip {
  val format = new DecimalFormat("0.0000")
  def apply(axis:Axis, enabled:Box[Boolean])(implicit shelf: Shelf) = new AxisTooltip(axis, enabled)
  val horizTabPainter = new GraphThreePartPainter(IconFactory.image("HorizontalLineLabel"))
  val vertTabPainter = new GraphThreePartPainterVertical(IconFactory.image("VerticalLineLabel"))
  val lineColor = SwingView.shadedBoxColor

  def drawAxisLine(canvas:GraphCanvas, v:Double, a:Axis, label:String, color:Option[Color]) = {
    canvas.clipToData()
    val dataArea = canvas.spaces.dataArea
    val start = canvas.spaces.toPixel(dataArea.axisPosition(a, v))
    val end = start + canvas.spaces.pixelArea.axisPerpVec2(a)

    canvas.lineWidth = 1
    canvas.color = color.getOrElse(AxisTooltip.lineColor)
    canvas.line(start, end)

    canvas.color = GraphAxis.fontColor
    canvas.fontSize = GraphAxis.fontSize

    val size = canvas.stringSize(label)

    val colorOffset = if (color == None) 0 else 12;

    //TODO combine code
    a match {
      case X => {
        AxisTooltip.vertTabPainter.paint(canvas, start + Vec2(-16, -4 - 23 - size.x - colorOffset), Vec2(16, size.x + 23 + colorOffset))
        canvas.color = SwingView.selectedTextColor
        canvas.string(label, start + Vec2(-3, -15 - colorOffset), Vec2(0, 0), -1)
        color.foreach(c => {
          val swatch = Area(start + Vec2(-11, -21), Vec2(7, 7))
          canvas.color = c
          canvas.fillRect(swatch)
          canvas.color = SwingView.selectedTextColor
          canvas.drawRect(swatch)
        })
      }
      case Y => {
        AxisTooltip.horizTabPainter.paint(canvas, start + Vec2(4, -16), Vec2(size.x + 23 + colorOffset, 16))
        canvas.color = SwingView.selectedTextColor
        canvas.string(label, start + Vec2(15 + colorOffset, -3), Vec2(0, 0), 0)
        color.foreach(c => {
          val swatch = Area(start + Vec2(14, -11), Vec2(7, 7))
          canvas.color = c
          canvas.fillRect(swatch)
          canvas.color = SwingView.selectedTextColor
          canvas.drawRect(swatch)
        })
      }
    }

    size.x + 23 + 4 + colorOffset

  }

}

class AxisTooltip(axis:Axis, enabled:Box[Boolean])(implicit shelf: Shelf) extends GraphLayer {

  private val value: Box[Option[Double]] = BoxNow(None)

  def paint(implicit txn: TxnR) = {

    val a = axis
    
    val maybeV = value()
    val e = enabled()

    (canvas:GraphCanvas) => {
      if (e) {
        maybeV.foreach(v => {
          val label = AxisTooltip.format.format(v)
          AxisTooltip.drawAxisLine(canvas, v, a, label, None)
        })
      }
    }
  }

  def onMouse(e:GraphMouseEvent)(implicit txn: Txn) = {
    if (enabled()) {
      e.eventType match {
        case MOVE => {
          val axisPosition = e.spaces.pixelArea.axisRelativePosition(Axis.other(axis), e.spaces.toPixel(e.dataPoint)) * (if (axis == X) -1 else 1)
          if (axisPosition <= 0 && axisPosition > -32) {
            value() = Some(e.dataPoint.onAxis(axis))
          } else {
            value() = None
          }
        }
        case _ => value() = None
      }
      false
    } else {
      false
    }

  }

  val dataBounds = BoxNow(None:Option[Area])

}

class GraphZoomer(
    val dataBounds: Box[Option[Area]],
    val manualBounds: Box[Option[Area]],
    val xAxis: Box[GraphZoomerAxis],
    val yAxis: Box[GraphZoomerAxis])(implicit shelf: Shelf) {

  def autoArea(implicit txn: Txn) = {
    dataBounds() match {
      case None => {
        //We have no data bounds, so use the axes required ranges,
        //or 0 to 1 in each axis if there are none.
        val xRange = xAxis().requiredRange().getOrElse((0d, 1d))
        val yRange = yAxis().requiredRange().getOrElse((0d, 1d))
        Area(Vec2(xRange._1, yRange._1), Vec2(xRange._2, yRange._2)).normalise
      }
      case Some(area) => {
        //We have a data bounds area, so pad it appropriately
        val auto = area.pad(Vec2(xAxis().paddingBefore(), yAxis().paddingBefore()), Vec2(xAxis().paddingAfter(), yAxis().paddingAfter()))

        val padX = xAxis().requiredRange().foldLeft(auto){(area, range) => area.extendToContain(Vec2(range._1, auto.origin.y)).extendToContain(Vec2(range._2, auto.origin.y))}
        val padY = yAxis().requiredRange().foldLeft(padX){(area, range) => area.extendToContain(Vec2(auto.origin.x, range._1)).extendToContain(Vec2(auto.origin.x, range._2))}

        padY
      }
    }
  }

  val dataArea = BoxNow.calc(implicit txn => {
    //Use manual bounds if specified, automatic area from data bounds etc.
    //Make sure that size is at least the minimum for each axis
    val a = manualBounds().getOrElse(autoArea)
    a.sizeAtLeast(Vec2(xAxis().minSize(), yAxis().minSize()))
  })
}

object GraphClickToSelectSeries{
  def apply[K](series: Box[List[Series[K]]], selectionOut: Box[Set[K]], enabled: Box[Boolean])(implicit shelf: Shelf) = new GraphClickToSelectSeries(series, selectionOut, enabled)
}

class GraphClickToSelectSeries[K](series: Box[List[Series[K]]], selectionOut: Box[Set[K]], enabled: Box[Boolean])(implicit shelf: Shelf) extends GraphLayer {

  def paint(implicit txn: TxnR) = (canvas:GraphCanvas) => {}

  def onMouse(e:GraphMouseEvent)(implicit txn: Txn) = {
    if (enabled()) {
      e.eventType match {
        case CLICK => {
          val selectedSeries = SeriesSelection.selectedSeries(series(), e)
          selectedSeries.foreach((ss) => selectionOut() = Set(ss.key))
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

case class GraphBasic(layers: Box[List[GraphLayer]], overlayers: Box[List[GraphLayer]], dataArea: Box[Area], borders: Box[Borders], highQuality: Box[Boolean])(implicit shelf: Shelf) extends Graph {}

object GraphBasic {
  
  def withSeries[K](
      series: Box[List[Series[K]]],
      xName: Box[String],
      yName: Box[String],
      borders: Box[Borders],
      zoomEnabled: Box[Boolean],
      manualBounds: Box[Option[Area]],
      xAxis: Box[GraphZoomerAxis],
      yAxis: Box[GraphZoomerAxis],
      selectEnabled: Box[Boolean],
      clickSelectEnabled: Box[Boolean],
      selection: Box[Set[K]],
      grabEnabled: Box[Boolean],
      seriesTooltipsEnabled: Box[Boolean],
      seriesTooltipsPrint: ((K, TxnR)=>String),
      axisTooltipsEnabled: Box[Boolean],
      extraMainLayers: List[GraphLayer],
      extraOverLayers: List[GraphLayer],
      highQuality: Box[Boolean],
      border: Color = SwingView.background,
      background: Color = Color.white
      )(implicit shelf: Shelf) = {

    val layers = BoxNow(
      extraMainLayers ::: List(
        new GraphBG(border, background),
        new GraphHighlight(),
        new GraphSeries(series, true),
        new GraphAxis(Y, 50),
        new GraphAxis(X),
        new GraphShadow(),
        new GraphSeries[K](series),
        new GraphOutline(),
        new GraphAxisTitle(X, xName),
        new GraphAxisTitle(Y, yName)
      )
    )

    val dataBounds = BoxNow.calc(implicit txn => {
      layers().foldLeft(None:Option[Area]){
        (areaOption, layer) => areaOption match {
          case None => layer.dataBounds()

          case Some(area) => layer.dataBounds() match {
            case None => Some(area)
            case Some(layerArea) => Some(area.extendToContain(layerArea))
          }
        }
      }
    })

    val zoomer = new GraphZoomer(dataBounds, manualBounds, xAxis, yAxis)

    val overlayers = BoxNow(
        //FIXME reinstate series tooltips
        /*SeriesTooltips.highlight(series, seriesTooltipsEnabled)) ::: */extraOverLayers ::: List(
        GraphZoomBox(BoxNow(new Color(0, 0, 200, 50)), BoxNow(new Color(100, 100, 200)), manualBounds, zoomEnabled),
        GraphSelectBox(series, BoxNow(new Color(0, 200, 0, 50)), BoxNow(new Color(100, 200, 100)), selection, selectEnabled),
        GraphGrab(grabEnabled, manualBounds, zoomer.dataArea),
        GraphClickToSelectSeries(series, selection, clickSelectEnabled),
        AxisTooltip(X, axisTooltipsEnabled),
        AxisTooltip(Y, axisTooltipsEnabled)//,
        //SeriesTooltips.string(series, seriesTooltipsEnabled, seriesTooltipsPrint)
      )
    )

    new GraphBasic(
      layers,
      overlayers,
      zoomer.dataArea,
      borders,
      highQuality
    )
  }
  
//  
//  def withBarsSelectByCat[C1, C2, K](
//      data: Ref[Map[(C1, C2), Bar[K]]],
//      cat1Print: (C1 => String) = (c: C1)=>c.toString, 
//      cat2Print: (C2 => String) = (c: C2)=>c.toString,
//      barWidth: Ref[Double] = Val(1.0), catPadding: Ref[Double] = Val(1.0), barPadding: Ref[Double] = Val(0.4),
//      yName:Ref[String] = Val("y"),
//      borders:Ref[Borders] = Val(Borders(16, 74, 53, 16)),
//      zoomEnabled:Ref[Boolean] = Val(true),
//      manualBounds:Var[Option[Area]] = Var(None),
//      xAxis:Ref[GraphZoomerAxis] = Val(GraphZoomerAxis()),
//      yAxis:Ref[GraphZoomerAxis] = Val(GraphZoomerAxis()),
//      selectEnabled:Ref[Boolean] = Val(false),
//      clickSelectEnabled:Ref[Boolean] = Val(true),
//      selection:Var[Set[(C1, C2)]] = Var(Set[(C1, C2)]()),
//      grabEnabled:Ref[Boolean] = Val(false),
//      barTooltipsEnabled:Ref[Boolean] = Val(true),
//      barTooltipsPrint:((C1, C2, Bar[K]) => String) = BarTooltips.defaultPrint[C1, C2, K],
//      axisTooltipsEnabled:Ref[Boolean] = Val(true),
//      extraMainLayers:List[GraphLayer] = List[GraphLayer](),
//      extraOverLayers:List[GraphLayer] = List[GraphLayer](),
//      highQuality: Ref[Boolean] = Val(true)
//      )(implicit ord1: Ordering[C1], ord2: Ordering[C2]) = {
//
//    val layers = ListVal[GraphLayer](
//      extraMainLayers ::: List(
//        new GraphBG(SwingView.background, Color.white),
//        new GraphHighlight(),
//        new GraphBars(data, barWidth, catPadding, barPadding, true)(ord1, ord2),  //Shadows
//        new GraphAxis(Y, 50),
//        new GraphBarAxis(data, barWidth, catPadding, barPadding, X, cat1Print, cat2Print)(ord1, ord2),
//        new GraphShadow(),
//        new GraphBars(data, barWidth, catPadding, barPadding, false)(ord1, ord2), //Data
//        new GraphOutline(),
//        new GraphAxisTitle(Y, yName)
//      )
//    )
//
//    val dataBounds = Cal{
//      layers().foldLeft(None:Option[Area]){
//        (areaOption, layer) => areaOption match {
//          case None => layer.dataBounds()
//
//          case Some(area) => layer.dataBounds() match {
//            case None => Some(area)
//            case Some(layerArea) => Some(area.extendToContain(layerArea))
//          }
//        }
//      }
//    }
//
//    val zoomer = new GraphZoomer(dataBounds, manualBounds, xAxis, yAxis)
//
//    val overlayers = ListVal[GraphLayer](
////      List(SeriesTooltips.highlight(series, seriesTooltipsEnabled)) ::: 
//        extraOverLayers ::: List(
//        GraphZoomBox(Val(new Color(0, 0, 200, 50)), Val(new Color(100, 100, 200)), manualBounds, zoomEnabled),
//        GraphSelectBarsByCatWithBox(data, selection, barWidth, catPadding, barPadding, selectEnabled, Val(new Color(0, 200, 0, 50)), Val(new Color(100, 200, 100))),
//        GraphGrab(grabEnabled, manualBounds, zoomer.dataArea),
//        GraphClickToSelectBarByCat(data, selection, barWidth, catPadding, barPadding, clickSelectEnabled),
//        AxisTooltip(Y, axisTooltipsEnabled),
//        BarTooltips.string(barTooltipsEnabled, data, barWidth, catPadding, barPadding, barTooltipsPrint)(ord1, ord2)
//      )
//    )
//
//    new GraphBasic(
//      layers,
//      overlayers,
//      zoomer.dataArea,
//      borders,
//      highQuality
//    )
//  }
// 
//    def withBarsSelectByKey[C1, C2, K](
//      data: Ref[Map[(C1, C2), Bar[K]]],
//      cat1Print: (C1 => String) = (c: C1)=>c.toString, 
//      cat2Print: (C2 => String) = (c: C2)=>c.toString,
//      barWidth: Ref[Double] = Val(1.0), catPadding: Ref[Double] = Val(1.0), barPadding: Ref[Double] = Val(0.4),
//      yName:Ref[String] = Val("y"),
//      borders:Ref[Borders] = Val(Borders(16, 74, 53, 16)),
//      zoomEnabled:Ref[Boolean] = Val(true),
//      manualBounds:Var[Option[Area]] = Var(None),
//      xAxis:Ref[GraphZoomerAxis] = Val(GraphZoomerAxis()),
//      yAxis:Ref[GraphZoomerAxis] = Val(GraphZoomerAxis()),
//      selectEnabled:Ref[Boolean] = Val(false),
//      clickSelectEnabled:Ref[Boolean] = Val(true),
//      selection:Var[Set[K]] = Var(Set[K]()),
//      grabEnabled:Ref[Boolean] = Val(false),
//      barTooltipsEnabled:Ref[Boolean] = Val(true),
//      barTooltipsPrint:((C1, C2, Bar[K]) => String) = BarTooltips.defaultPrint[C1, C2, K],
//      axisTooltipsEnabled:Ref[Boolean] = Val(true),
//      extraMainLayers:List[GraphLayer] = List[GraphLayer](),
//      extraOverLayers:List[GraphLayer] = List[GraphLayer](),
//      highQuality: Ref[Boolean] = Val(true)
//      )(implicit ord1: Ordering[C1], ord2: Ordering[C2]) = {
//
//    val layers = ListVal[GraphLayer](
//      extraMainLayers ::: List(
//        new GraphBG(SwingView.background, Color.white),
//        new GraphHighlight(),
//        new GraphBars(data, barWidth, catPadding, barPadding, true)(ord1, ord2),  //Shadows
//        new GraphAxis(Y, 50),
//        new GraphBarAxis(data, barWidth, catPadding, barPadding, X, cat1Print, cat2Print)(ord1, ord2),
//        new GraphShadow(),
//        new GraphBars(data, barWidth, catPadding, barPadding, false)(ord1, ord2), //Data
//        new GraphOutline(),
//        new GraphAxisTitle(Y, yName)
//      )
//    )
//
//    val dataBounds = Cal{
//      layers().foldLeft(None:Option[Area]){
//        (areaOption, layer) => areaOption match {
//          case None => layer.dataBounds()
//
//          case Some(area) => layer.dataBounds() match {
//            case None => Some(area)
//            case Some(layerArea) => Some(area.extendToContain(layerArea))
//          }
//        }
//      }
//    }
//
//    val zoomer = new GraphZoomer(dataBounds, manualBounds, xAxis, yAxis)
//
//    val overlayers = ListVal[GraphLayer](
////      List(SeriesTooltips.highlight(series, seriesTooltipsEnabled)) ::: 
//        extraOverLayers ::: List(
//        GraphZoomBox(Val(new Color(0, 0, 200, 50)), Val(new Color(100, 100, 200)), manualBounds, zoomEnabled),
//        GraphSelectBarsByKeyWithBox(data, selection, barWidth, catPadding, barPadding, selectEnabled, Val(new Color(0, 200, 0, 50)), Val(new Color(100, 200, 100))),
//        GraphGrab(grabEnabled, manualBounds, zoomer.dataArea),
//        GraphClickToSelectBarByKey(data, selection, barWidth, catPadding, barPadding, clickSelectEnabled),
//        AxisTooltip(Y, axisTooltipsEnabled),
//        BarTooltips.string(barTooltipsEnabled, data, barWidth, catPadding, barPadding, barTooltipsPrint)(ord1, ord2)
//      )
//    )
//
//    new GraphBasic(
//      layers,
//      overlayers,
//      zoomer.dataArea,
//      borders,
//      highQuality
//    )
//  }
 
}
