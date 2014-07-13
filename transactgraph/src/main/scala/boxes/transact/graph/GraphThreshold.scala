package boxes.transact.graph

import boxes.graph.Axis
import Axis._
import boxes.graph.GraphMouseEventType._
import java.awt.Color
import java.text.DecimalFormat
import java.util.concurrent.atomic.AtomicReference
import boxes.transact._
import boxes.graph.GraphCanvas
import boxes.graph.Vec2
import boxes.graph.GraphMouseEvent
import boxes.graph.Area

object GraphThreshold {
  val format = new DecimalFormat("0.00")
  val handleRadius = 3

  def apply(axis: BoxR[Axis], value: Box[Double], color: BoxR[Color], name: BoxR[String], enabled: BoxR[Boolean])(implicit shelf: Shelf) = new GraphThreshold(axis, value, color, name, enabled)
}

class GraphThreshold(axis: BoxR[Axis], value: Box[Double], color: BoxR[Color], name: BoxR[String], enabled: BoxR[Boolean])(implicit shelf: Shelf) extends GraphLayer {

  private val labelWidth = new AtomicReference[Double](0d)

  def paint(implicit txn: TxnR) = {
    val c = color()
    val a = axis()
    val v = value()
    val n = name()

    (canvas:GraphCanvas) => {
      val label = n + ": " + GraphThreshold.format.format(v)
      labelWidth.set(AxisTooltip.drawAxisLine(canvas, v, a, label, Some(c)))
    }
  }

  private var pressOffset:Option[Double] = None

  def dataPointValue(point: Vec2)(implicit txn: TxnR) = axis() match {
    case X => point.x
    case Y => point.y
  }
  
  def onMouse(e: GraphMouseEvent)(implicit txn: Txn) = {
    if (enabled()) {
      val pixelPoint = e.spaces.toPixel(e.dataPoint)
      val valuePoint = e.spaces.toPixel(e.spaces.dataArea.axisPosition(axis(), value()))
      val pixelPerpDistance = (valuePoint - pixelPoint).onAxis(axis())
      val pixelDistance = (valuePoint - pixelPoint).onAxis(Axis.other(axis())) * (if (axis() == X) 1 else -1)
      val insideHandle = ((pixelPerpDistance > -2 && pixelPerpDistance < 18 && pixelDistance > 0 && pixelDistance < labelWidth.get()) || math.abs(pixelPerpDistance) < GraphThreshold.handleRadius)
      e.eventType match {
        case CLICK => insideHandle  //Consume clicks in handle - may use later
        case PRESS => {
          if (insideHandle) {
            pressOffset = Some(value() - dataPointValue(e.dataPoint))
            true
          } else {
            false
          }
        }
        case DRAG => pressOffset match {
          case Some(offset) => {
            value() = dataPointValue(e.dataPoint) + offset
            true
          }
          case None => false
        }
        case RELEASE => pressOffset match {
          case Some(offset) => {
            value() = dataPointValue(e.dataPoint) + offset
            pressOffset = None
            true
          }
          case None => false
        }
        case _ => false
      }
    } else {
      false
    }
  }

  val dataBounds = BoxNow(None:Option[Area])

}