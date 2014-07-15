package boxes.transact.swing.graph

import boxes.swing.icons.IconFactory
import boxes.transact.Box
import boxes.transact.graph.Graph
import boxes.transact.Shelf
import boxes.graph.Series
import boxes.graph.GraphZoomerAxis
import boxes.graph.Borders
import boxes.transact.graph.GraphLayer
import boxes.swing.SwingView
import boxes.transact.BoxNow
import boxes.graph.Vec2
import boxes.swing.GraphBuffer
import javax.swing.JPanel
import java.awt.Graphics
import java.awt.event.ComponentListener
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import boxes.graph.GraphMouseEventType
import boxes.graph.GraphMouseButton
import GraphMouseEventType._
import GraphMouseButton._
import boxes.graph.GraphMouseEvent
import java.awt.event.MouseMotionListener
import java.awt.event.MouseListener
import boxes.graph.GraphSpaces
import boxes.graph.Area
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.AlphaComposite
import java.awt.BorderLayout
import java.awt.geom.Rectangle2D
import boxes.swing.GraphCanvasFromGraphics2D
import boxes.transact.graph.GraphBusy
import boxes.swing.LinkingJPanel
import boxes.transact.TxnR
import boxes.transact.Txn

object GraphSwingBGView {
  def apply(graph:Box[_ <: Graph])(implicit shelf: Shelf) = new GraphSwingBGView(graph)
}

//TODO remove shared code from GraphSwingView and GraphSwingBGView
class GraphSwingBGView(graph: Box[_ <: Graph])(implicit shelf: Shelf) extends SwingView {

  val componentSize = BoxNow(Vec2(400, 400))

  val overBuffer = new GraphBuffer()

  val concBuffer = new GraphBuffer()
  val concBackBuffer = new GraphBuffer()

  val busyAlpha = BoxNow(0.0)
  val busyLayer = new GraphBusy(busyAlpha)

  val component = new LinkingJPanel(this, new BorderLayout()) {

    override def paintComponent(gr: Graphics) {
      concBuffer.lock.synchronized{
        gr.drawImage(concBuffer.image, 0, 0, null)
      }
      overBuffer.lock.synchronized{
        gr.drawImage(overBuffer.image, 0, 0, null)
      }
    }

    def updateSize() {
      val v = Vec2(this.getWidth, this.getHeight)
      componentSize.now() = v
    }

    this.addComponentListener(new ComponentListener(){
      override def componentResized(e:ComponentEvent) {
        updateSize()
      }
      override def componentMoved(e:ComponentEvent) {}
      override def componentShown(e:ComponentEvent) {}
      override def componentHidden(e:ComponentEvent) {}
    })

    def fireMouse(e:MouseEvent, eventType:GraphMouseEventType) {
      shelf.transact(implicit txn => {
        val s = buildSpaces
        val p = e.getPoint
        val b = e.getButton match {
          case MouseEvent.BUTTON1 => LEFT
          case MouseEvent.BUTTON2 => MIDDLE
          case MouseEvent.BUTTON3 => RIGHT
          case _ => NONE
        }
        var x = p.x
        var y = p.y
        val w = getWidth
        val h = getHeight
        if (x < 0) {
          x = 0
        } else if (x > w) {
          x = w
        }
        if (y < 0) {
          y = 0
        } else if (y > h) {
          y = h
        }
  
        val dataPoint = s.toData(Vec2(x, y))
        val gme = GraphMouseEvent(s, dataPoint, eventType, b)
        val consumedGME = GraphMouseEvent(s, dataPoint, CONSUMED, b)

        val consumed = graph().overlayers().foldLeft(false)((consumed, layer) => if(!consumed) layer.onMouse(gme) else {layer.onMouse(consumedGME); true})
        graph().layers().foldLeft(consumed)((consumed, layer) => if(!consumed) layer.onMouse(gme) else {layer.onMouse(consumedGME); true})
      })

    }

    this.addMouseMotionListener(new MouseMotionListener() {
      def mouseDragged(e: MouseEvent) {
        fireMouse(e, DRAG)
      }
      def mouseMoved(e: MouseEvent) {
        fireMouse(e, MOVE)
      }
    })

    this.addMouseListener(new MouseListener(){
      def mouseClicked(e: MouseEvent) {
        fireMouse(e, CLICK)
      }
      def mousePressed(e: MouseEvent) {
        fireMouse(e, PRESS)
      }
      def mouseReleased(e: MouseEvent) {
        fireMouse(e, RELEASE)
      }
      def mouseEntered(e: MouseEvent) {
        fireMouse(e, ENTER)
      }
      def mouseExited(e: MouseEvent) {
        fireMouse(e, EXIT)
      }
    })
  }

  val bg = shelf.auto(implicit txn => {

    val paints = graph().layers().map(layer => layer.paint)
    val spaces = buildSpaces
    val highQuality = graph().highQuality()

    //TODO Would be nice to fade the busy icon in after a short delay, to avoid flickering the icon, or flashing it
    //for trivial redraws, then (perhaps) fade out later. This could then allow for an animated progress indicator.
    //Requires a Timer or similar, so a little fiddly
    
    //Note we use "now" to perform an immediate transaction, otherwise the alpha would not
    //change until after the whole redraw is complete - we really should be the 
    //only ones changing the busy alpha, so we expect this to succeed immediately
    busyAlpha.now() = 1.0
    
    concBackBuffer.ensureSize(spaces.componentArea.size)
    val g = concBackBuffer.image.getGraphics.asInstanceOf[Graphics2D]
    concBackBuffer.clear()
    //TODO progress indicator, would need to pass a Var[Double] to each paint method, then estimate proportion
    //of total time taken by each layer, or perhaps have a two-level progress indicator to show layers complete out
    //of total, and progress on current layer, might look quite cool.
    paints.foreach(paint => paint.apply(new GraphCanvasFromGraphics2D(g.create().asInstanceOf[Graphics2D], spaces, highQuality)))
    g.dispose

    concBuffer.lock.synchronized{
      concBuffer.ensureSize(spaces.componentArea.size)
      concBuffer.clear()
      concBuffer.image.getGraphics.asInstanceOf[Graphics2D].drawImage(concBackBuffer.image, 0, 0, null)
    }
    
    //Use "now" as above.
    busyAlpha.now() = 0.0

    replaceUpdate{
      component.repaint();
    }
  })

  val overView = shelf.view(implicit txn => {
    drawBuffer(overBuffer, graph().overlayers() ::: List(busyLayer))
    replaceUpdate {
      component.repaint()
    }
  })

  def buildSpaces(implicit txn: TxnR) = {
    val size = componentSize()
    val area = graph().dataArea()
    val borders = graph().borders()

    val w = size.x.asInstanceOf[Int]
    val h = size.y.asInstanceOf[Int]

    val l = borders.left.asInstanceOf[Int]
    val r = borders.right.asInstanceOf[Int]
    val t = borders.top.asInstanceOf[Int]
    val b = borders.bottom.asInstanceOf[Int]
    val dw = w - l - r
    val dh = h - t - b

    GraphSpaces(area, Area(Vec2(l, t+dh), Vec2(dw, -dh)), Area(Vec2.zero, size))
  }

  def drawBuffer(buffer:GraphBuffer, layers:List[GraphLayer])(implicit txn: TxnR) {
    buffer.lock.synchronized{
      val spaces = buildSpaces

      buffer.ensureSize(spaces.componentArea.size)
      buffer.clear()

      val g = buffer.image.getGraphics.asInstanceOf[Graphics2D]

      //Each layer paints on a fresh canvas, to avoid side effects from one affecting the next
      layers.foreach(layer => {
        val paint = layer.paint
        val highQuality = graph().highQuality()
        paint.apply(new GraphCanvasFromGraphics2D(g.create().asInstanceOf[Graphics2D], spaces, highQuality))
      })

      g.dispose
    }
  }
}


