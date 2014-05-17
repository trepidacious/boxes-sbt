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
import java.awt.geom.Rectangle2D
import boxes.swing.GraphCanvasFromGraphics2D
import boxes.transact.graph.GraphBusy

object GraphSwingView {

  def icon(name:String) = IconFactory.icon(name)

  val zoom = icon("Zoom")
  val zoomIn = icon("ZoomIn")
  val zoomOut = icon("ZoomOut")
  val zoomSelect = icon("ZoomSelect")
  val boxSelect = icon("BoxSelect")
  val grabHand = icon("GrabHand")
  val move = icon("Move")

  def apply(graph:Box[_ <: Graph])(implicit shelf: Shelf) = new GraphSwingView(graph)

//  //TODO can this be done with currying?
//  //Make a panel with series, using normal view
//  def panelWithSeries[K](
//    series: Box[List[Series[K]]],
//    selection: Box[Set[K]],
//
//    xName: Box[String],
//    yName: Box[String],
//    
//    xAxis: Box[GraphZoomerAxis],
//    yAxis: Box[GraphZoomerAxis],
//
//    graphName: Box[String],
//    
//    zoom: Boolean = true,
//    select: Boolean = false,
//    grab: Boolean = false,
//    
//    seriesTooltips: Boolean = false,
//    axisTooltips: Boolean = true,
//    
//    borders: Box[Borders],
//    extraMainLayers: List[GraphLayer] = List[GraphLayer](),
//    extraOverLayers: List[GraphLayer] = List[GraphLayer]()
//  )(implicit shelf: Shelf) = GraphSwing.panelWithSeries(g => GraphSwingView(g))(series, selection, xName, yName, xAxis, yAxis, graphName, zoom, select, grab, seriesTooltips, axisTooltips, borders, extraMainLayers, extraOverLayers)

}


//TODO remove shared code from GraphSwingView and GraphSwingBGView
class GraphSwingView(graph: Box[_ <: Graph])(implicit shelf: Shelf) extends SwingView {

  val componentSize = BoxNow(Vec2(10, 10))

  val mainBuffer = new GraphBuffer()
  val overBuffer = new GraphBuffer()

  val concBuffer = new GraphBuffer()
  val concBackBuffer = new GraphBuffer()

  val component = new JPanel() {

    override def paintComponent(gr: Graphics) {
      mainBuffer.lock.synchronized{
        gr.drawImage(mainBuffer.image, 0, 0, null)
      }
      concBuffer.lock.synchronized{
        gr.drawImage(concBuffer.image, 0, 0, null)
      }
      overBuffer.lock.synchronized{
        gr.drawImage(overBuffer.image, 0, 0, null)
      }
    }

    def updateSize() {
      componentSize.now() = Vec2(this.getWidth, this.getHeight)
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

      shelf.read(implicit txn => {
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

  val mainView = shelf.view(implicit txn => {
    drawBuffer(mainBuffer, graph().layers())
    replaceUpdate {
      component.repaint()
    }
  })

  val overView = shelf.view(implicit txn => {
    drawBuffer(overBuffer, graph().overlayers())
    replaceUpdate {
      component.repaint()
    }
  })

  def buildSpaces = {
    shelf.read(implicit txn => {
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
    })
  }

  def drawBuffer(buffer:GraphBuffer, layers:List[GraphLayer]) {
    buffer.lock.synchronized{
      val spaces = buildSpaces

      val w = spaces.componentArea.size.x.asInstanceOf[Int]
      val h = spaces.componentArea.size.y.asInstanceOf[Int]

      buffer.ensureSize(spaces.componentArea.size)

      val g = buffer.image.getGraphics.asInstanceOf[Graphics2D]
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

      val oldComposite = g.getComposite
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR, 0.0f))
      g.fill(new Rectangle2D.Double(0,0,w,h))
      g.setComposite(oldComposite)

      //Each layer paints on a fresh canvas, to avoid side effects from one affecting the next
      layers.foreach(layer => {
        val paint = layer.paint()
        shelf.read(implicit txn => {
          val highQuality = graph().highQuality()
          paint.apply(new GraphCanvasFromGraphics2D(g.create().asInstanceOf[Graphics2D], spaces, highQuality))
        })
      })

      g.dispose
    }
  }
}


object GraphSwingBGView {
//  def apply(graph: Box[_ <: Graph])(implicit shelf: Shelf) = new GraphSwingBGView(graph)
  
//  //Make a panel with series, using bg view
//  def panelWithSeries[K](
//    series: Box[List[Series[K]]],
//    selection: Box[Set[K]],
//
//    xName: Box[String],
//    yName: Box[String],
//    
//    xAxis: Box[GraphZoomerAxis],
//    yAxis: Box[GraphZoomerAxis],
//
//    graphName: Box[String],
//    
//    zoom: Boolean = true,
//    select: Boolean = false,
//    grab: Boolean = false,
//    
//    seriesTooltips: Boolean = false,
//    axisTooltips: Boolean = true,
//    
//    borders: Box[Borders],
//    extraMainLayers: List[GraphLayer] = List[GraphLayer](),
//    extraOverLayers: List[GraphLayer] = List[GraphLayer]()
//  )(implicit shelf: Shelf) = GraphSwing.panelWithSeries(g => GraphSwingBGView(g))(series, selection, xName, yName, xAxis, yAxis, graphName, zoom, select, grab, seriesTooltips, axisTooltips, borders, extraMainLayers, extraOverLayers)
}

object GraphSwing {
  
//  def panelWithSeries[K](makeView: Box[_ <: Graph] => SwingView)(
//    series: Box[List[Series[K]]],
//    selection: Box[Set[K]],
//
//    xName: Box[String],
//    yName: Box[String],
//    
//    xAxis: Box[GraphZoomerAxis],
//    yAxis: Box[GraphZoomerAxis],
//
//    graphName: Box[String],
//    
//    zoom: Boolean = true,
//    select: Boolean = false,
//    grab: Boolean = false,
//    
//    seriesTooltips: Boolean = false,
//    axisTooltips: Boolean = true,
//    
//    borders: Box[Borders],
//    extraMainLayers:List[GraphLayer] = List[GraphLayer](),
//    extraOverLayers:List[GraphLayer] = List[GraphLayer]()
//  )(implicit shelf: Shelf) = {
//        
//    val zoomEnabled = if (zoom) Some(BoxNow(true)) else None
//    val selectEnabled = if (select) Some(BoxNow(false)) else None
//    val grabEnabled = if (grab) Some(BoxNow(false)) else None
//
//    val modes = List(zoomEnabled, selectEnabled, grabEnabled).collect{case Some(v) => v}
//    if (!modes.isEmpty) RadioReaction(modes:_*)
//    
//    val axisTooltipsEnabled = if (axisTooltips) Some(Var(true)) else None
//    val seriesTooltipsEnabled = if (seriesTooltips) Some(Var(true)) else None
//
//    val manualBounds = Var(None:Option[Area])
//
//    import boxes.graph.Axis._
//
//    val graph = Var (
//      GraphBasic.withSeries (
//        ColorSeriesBySelection(series, selection),
//        xName = xName,
//        yName = yName,
//        zoomEnabled = zoomEnabled.getOrElse(Val(false)),
//        manualBounds = manualBounds,
//        xAxis = xAxis,
//        yAxis = yAxis,
//        selectEnabled = selectEnabled.getOrElse(Val(false)),
//        selection = selection,
//        grabEnabled = grabEnabled.getOrElse(Val(false)),
//        seriesTooltipsEnabled = seriesTooltipsEnabled.getOrElse(Val(false)),
//        axisTooltipsEnabled = axisTooltipsEnabled.getOrElse(Val(false))
//       // extraOverLayers = List(xThreshold, yThreshold)
//      )
//    )
//
//    val v = makeView(graph)
//
//    //Zoom out by clearing manual bounds to None
//    val zoomOutButton = if(zoom) Some(SwingBarButton(SwingOp("", Some(GraphSwingView.zoomOut), SetOp(manualBounds, None:Option[Area])))) else None
//
//    val zoomEnabledView = zoomEnabled.map(BooleanView(_, "", BooleanControlType.TOOLBARBUTTON, Some(GraphSwingView.zoomSelect), false))
//
//    val selectEnabledView = selectEnabled.map(BooleanView(_, "", BooleanControlType.TOOLBARBUTTON, Some(GraphSwingView.boxSelect), false))
//
//    val grabEnabledView = grabEnabled.map(BooleanView(_, "", BooleanControlType.TOOLBARBUTTON, Some(GraphSwingView.move), false))
//
//    val properties = List(("Axis Tooltips", axisTooltipsEnabled), ("Series Tooltips", seriesTooltipsEnabled)).collect{case (k,Some(v)) => (k, v)}
//    
//    val graphProperties = if (properties.isEmpty) None else {
//      Some(properties.foldLeft(SheetBuilder().blankTop()){case (b, (s, v)) => b.view(s, BooleanView(v))}.panel())
//    } 
//
//    val settingsPopup = graphProperties.map(panel => BoxesPopupView(icon = Some(SwingView.wrench), popupContents = panel))
//
//    val buttons = SwingButtonBar()
//                    .addSwingView(selectEnabledView)
//                    .addSwingView(grabEnabledView)
//                    .addSwingView(zoomEnabledView)
//                    .addComponent(zoomOutButton)
//                    .addSwingView(settingsPopup)
//                  .buildWithListStyleComponent(EmbossedLabelView(graphName).component())
//
//    val panel = new JPanel(new BorderLayout())
//    panel.add(v.component, BorderLayout.CENTER)
//
//    panel.add(buttons, BorderLayout.SOUTH)
//
//    panel
//  }
}


//class GraphSwingBGView(graph: Box[_ <: Graph])(implicit shelf: Shelf) extends SwingView {
//
//  val componentSize = BoxNow(Vec2(10, 10))
//
//  val overBuffer = new GraphBuffer()
//
//  val concBuffer = new GraphBuffer()
//  val concBackBuffer = new GraphBuffer()
//
//  val busyAlpha = BoxNow(0d)
//  val busyLayer = new GraphBusy(busyAlpha)
//
//  val component = new JPanel() {
//
//    override def paintComponent(gr:Graphics) {
//      concBuffer.lock.synchronized{
//        gr.drawImage(concBuffer.image, 0, 0, null)
//      }
//      overBuffer.lock.synchronized{
//        gr.drawImage(overBuffer.image, 0, 0, null)
//      }
//    }
//
//    def updateSize() {
//      componentSize.now() = Vec2(this.getWidth, this.getHeight)
//    }
//
//    this.addComponentListener(new ComponentListener(){
//      override def componentResized(e:ComponentEvent) {
//        updateSize()
//      }
//      override def componentMoved(e:ComponentEvent) {}
//      override def componentShown(e:ComponentEvent) {}
//      override def componentHidden(e:ComponentEvent) {}
//    })
//
//    def fireMouse(e:MouseEvent, eventType:GraphMouseEventType) {
//      val s = buildSpaces
//      val p = e.getPoint
//      val b = e.getButton match {
//        case MouseEvent.BUTTON1 => LEFT
//        case MouseEvent.BUTTON2 => MIDDLE
//        case MouseEvent.BUTTON3 => RIGHT
//        case _ => NONE
//      }
//      var x = p.x
//      var y = p.y
//      val w = getWidth
//      val h = getHeight
//      if (x < 0) {
//        x = 0
//      } else if (x > w) {
//        x = w
//      }
//      if (y < 0) {
//        y = 0
//      } else if (y > h) {
//        y = h
//      }
//
//      val dataPoint = s.toData(Vec2(x, y))
//      val gme = GraphMouseEvent(s, dataPoint, eventType, b)
//      val consumedGME = GraphMouseEvent(s, dataPoint, CONSUMED, b)
//
//      shelf.read(implicit txn => {
//        val consumed = graph().overlayers().foldLeft(false)((consumed, layer) => if(!consumed) layer.onMouse(gme) else {layer.onMouse(consumedGME); true})
//        graph().layers().foldLeft(consumed)((consumed, layer) => if(!consumed) layer.onMouse(gme) else {layer.onMouse(consumedGME); true})        
//      })
//    }
//
//    this.addMouseMotionListener(new MouseMotionListener() {
//      def mouseDragged(e: MouseEvent) {
//        fireMouse(e, DRAG)
//      }
//      def mouseMoved(e: MouseEvent) {
//        fireMouse(e, MOVE)
//      }
//    })
//
//    this.addMouseListener(new MouseListener(){
//      def mouseClicked(e: MouseEvent) {
//        fireMouse(e, CLICK)
//      }
//      def mousePressed(e: MouseEvent) {
//        fireMouse(e, PRESS)
//      }
//      def mouseReleased(e: MouseEvent) {
//        fireMouse(e, RELEASE)
//      }
//      def mouseEntered(e: MouseEvent) {
//        fireMouse(e, ENTER)
//      }
//      def mouseExited(e: MouseEvent) {
//        fireMouse(e, EXIT)
//      }
//    })
//  }
//
//  val bg = BackgroundReaction{
//
//    val paints = graph().layers().map(layer => layer.paint())
//    val spaces = buildSpaces
//    val highQuality = graph().highQuality()
//
//    (cancel:AtomicBoolean) => {
//      Box.withoutReading {
//        //TODO Would be nice to fade the busy icon in after a short delay, to avoid flickering the icon, or flashing it
//        //for trivial redraws, then (perhaps) fade out later. This could then allow for an animated progress indicator.
//        //Requires a Timer or similar, so a little fiddly
//        busyAlpha() = 1
//        concBackBuffer.ensureSize(spaces.componentArea.size)
//        val g = concBackBuffer.image.getGraphics.asInstanceOf[Graphics2D]
//        concBackBuffer.clear()
//        //TODO progress indicator, would need to pass a Var[Double] to each paint method, then estimate proportion
//        //of total time taken by each layer, or perhaps have a two-level progress indicator to show layers complete out
//        //of total, and progress on current layer, might look quite cool.
//        paints.foreach(paint => paint.apply(new GraphCanvasFromGraphics2D(g.create().asInstanceOf[Graphics2D], spaces, highQuality)))
//        g.dispose
//
//        concBuffer.lock.synchronized{
//          concBuffer.ensureSize(spaces.componentArea.size)
//          concBuffer.clear()
//          concBuffer.image.getGraphics.asInstanceOf[Graphics2D].drawImage(concBackBuffer.image, 0, 0, null)
//        }
//        busyAlpha() = 0
//
//        replaceUpdate{
//          component.repaint();
//        }
//      }
//
//    }
//  }
//
//  val overView = View {
//    drawBuffer(overBuffer, graph().overlayers() ::: List(busyLayer))
//    replaceUpdate {
//      component.repaint()
//    }
//  }
//
//  def buildSpaces = {
//    val size = componentSize()
//    val area = graph().dataArea()
//    val borders = graph().borders()
//
//    val w = size.x.asInstanceOf[Int]
//    val h = size.y.asInstanceOf[Int]
//
//    val l = borders.left.asInstanceOf[Int]
//    val r = borders.right.asInstanceOf[Int]
//    val t = borders.top.asInstanceOf[Int]
//    val b = borders.bottom.asInstanceOf[Int]
//    val dw = w - l - r
//    val dh = h - t - b
//
//    GraphSpaces(area, Area(Vec2(l, t+dh), Vec2(dw, -dh)), Area(Vec2.zero, size))
//  }
//
//  def drawBuffer(buffer:GraphBuffer, layers:List[GraphLayer]) {
//    buffer.lock.synchronized{
//      val spaces = buildSpaces
//
//      buffer.ensureSize(spaces.componentArea.size)
//      buffer.clear()
//
//      val g = buffer.image.getGraphics.asInstanceOf[Graphics2D]
//
//      //Each layer paints on a fresh canvas, to avoid side effects from one affecting the next
//      layers.foreach(layer => {
//        val paint = layer.paint()
//        
//        Box.withoutReading {
//          paint.apply(new GraphCanvasFromGraphics2D(g.create().asInstanceOf[Graphics2D], spaces, graph().highQuality()))
//        }
//      })
//
//      g.dispose
//    }
//  }
//}
