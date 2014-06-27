package boxes.transact.swing.demo

import boxes.transact._
import boxes.transact.Includes._
import boxes.transact.Implicits._
import boxes.transact.data._
import boxes.transact.swing.views._
import boxes.transact.op._
import boxes.transact.swing.op._
import boxes.transact.swing.layout._
import boxes.transact.reaction._
import java.awt.{Dimension, BorderLayout, GridLayout, Color}
import boxes.swing.icons.IconFactory
import javax.swing.JPanel
import javax.swing.JFrame
import boxes.swing.EmbossedLabel
import boxes.swing.SwingView
import boxes.graph.Area
import boxes.transact.reaction.RadioReaction
import boxes.graph.Series
import boxes.graph.Vec2
import boxes.graph.SeriesStyles
import boxes.transact.graph.GraphBasic
import boxes.graph.Borders
import boxes.graph.GraphZoomerAxis
import boxes.transact.swing.graph.GraphSwingView
import boxes.transact.swing.views.BooleanView
import boxes.transact.swing.views.PopupView
import scala.math.Ordering
import boxes.transact.graph.ColorSeriesBySelection

object SineDemo {

  //This enables use of default values and allows passing the Boxes in as parameters, 
  //at the expense of some boilerplate - there may be a nicer way to do this.
  //
  //Use it like this:
  //
  //    val s2 = transact(implicit txn => {
  //      val b = new Sine2Builder
  //      val s = new b.Sine2()
  //    })
  //
  class Sine2Builder()(implicit txn: Txn) {
    class Sine2(
        val name: Box[String] = Box("Sine"), 
        val phase: Box[Double] = Box(0d), 
        val amplitude: Box[Double] = Box(1d), 
        val enabled: Box[Boolean] = Box(true), 
        val points: Box[Boolean] = Box(false), 
        val description: Box[String] = Box("Default Description"))
  }
  
  //This won't work with default values
  class Sine(implicit txn: Txn) {
    val name = Box("Sine")
    val phase = Box(0d)
    val amplitude = Box(1d)
    val enabled = Box(true)
    val points = Box(false)
    val description = Box("Default Description")
  }
  
  object Sine {
    def apply()(implicit txn: Txn) = new Sine
  }

  def buildLedgerMulti()(implicit shelf: Shelf) = {
    
    val list = shelf.transact(implicit txn => {
      val sines = Range(0, 10).map(i=>{
        val s = Sine()
        s.name() = "Sine " + i
        s.phase() = i/40d
        s.amplitude() = 1
        s
      }).toVector
      Box(sines)
    })

    val view = LensRecordView[Sine](
      MBoxLens("Name", _.name),
      MBoxLens("Phase", _.phase),
      MBoxLens("Amplitude", _.amplitude),
      MBoxLens("Enabled", _.enabled),
      MBoxLens("Points", _.points)
    )

    val sel = ListIndices.now(list, true)
    val ledger = ListLedgerBox.now(list, view)

    val ledgerView = LedgerView.multiSelectionScroll(ledger, sel.indices, true)

    val add = new ListMultiAddOp(list, sel.indices, implicit txn => List(Sine()))

    val delete = new ListMultiDeleteOp[Sine](list, sel.indices, t=>Unit)

    val up = new ListMultiMoveOp[Sine](list, sel.indices, true)

    val down = new ListMultiMoveOp[Sine](list, sel.indices, false)

//    val firstSelected = BoxNow.calc(implicit txn => if (sel.selected().size == 1) sel.selected().headOption else None)
    val firstSelected = BoxNow.calc(implicit txn => if (sel.selected().size > 0) Some(list().apply(sel.indices().min(Ordering.Int))) else None)

    val popup = PopupView(icon = Some(IconFactory.icon("zoom")), popupContents = properties(firstSelected))

    val buttons = SwingButtonBar().add(add).add(delete).add(up).add(down).add(popup).buildWithListStyleComponent(EmbossedLabel("Sine Table"))

    val mainPanel = new JPanel(new BorderLayout())
    mainPanel.add(ledgerView.component, BorderLayout.CENTER)
    mainPanel.add(buttons, BorderLayout.SOUTH)

    (mainPanel, list, sel, firstSelected)
  }

  def buildGraphPanel(sines: Box[Vector[Sine]], indices: ListIndices[Sine])(implicit shelf: Shelf) = {

    val selectEnabled = BoxNow(false)
    val zoomEnabled = BoxNow(true)
    val grabEnabled = BoxNow(false)
    val axisTooltipsEnabled = BoxNow(true)
    val seriesTooltipsEnabled = BoxNow(true)
    val manualBounds = BoxNow(None:Option[Area])
    RadioReaction.now(selectEnabled, zoomEnabled, grabEnabled)

    val series = BoxNow.calc(implicit txn => {
      sines().zipWithIndex.map{case (s, i) => 
        Series(i,
          if (s.enabled()) Range(0, 100).map(x => x/100d).map(x => Vec2(x, math.sin((x + s.phase()) * 2 * 3.1415) * s.amplitude())).toList else List[Vec2](),
          Color.getHSBColor((9-i)/14f, 1f, 1f),
          2,
          true,
          if (s.points()) SeriesStyles.cross else SeriesStyles.line
        )
      }.toList
    })

    import boxes.graph.Axis._

    //TODO port GraphThreshold to transact
//    val x = BoxNow(0.5d)
//    val xThreshold = GraphThreshold(X, x, Color.blue, "X Threshold", true)
//
//    val y = BoxNow(0.5d)
//    val yThreshold = GraphThreshold(Y, y, Color.red, "Y Threshold", true)

    val graph = BoxNow (
        GraphBasic.withSeries[Int](
//          series,             //series
          ColorSeriesBySelection(series, indices.indices),             //series
          BoxNow("x"),                      //xName
          BoxNow("y"),                      //yName
          BoxNow(Borders(16, 74, 53, 16)),  //borders
          zoomEnabled,                      //zoomEnabled
          manualBounds,                     //manualBounds
          BoxNow(GraphZoomerAxis()),        //xAxis
          BoxNow(GraphZoomerAxis()),        //yAxis
          selectEnabled,                    //selectEnabled
          BoxNow(true),                    //clickSelectEnabled
          indices.indices,                  //selection
          grabEnabled,                      //grabEnabled
          seriesTooltipsEnabled,            //seriesTooltipsEnabled
          //TODO add implicit to txn - should be possible?
          (i: Int, txn: TxnR) => sines()(txn).apply(i).toString(),  //seriesTooltipsPrint
          axisTooltipsEnabled,              //axisTooltipsEnabled
          Nil,                              //extraMainLayers
          Nil,                              //extraOverLayers
          BoxNow(true),                     //highQuality
          SwingView.background,             //border
          Color.white)                      //background
//      GraphBasic.withSeries (
//        ColorSeriesBySelection(series, indices),
//        xName = "X (Time)",
//        yName = "Y (Intensity)",
//        zoomEnabled = zoomEnabled,
//        manualBounds = manualBounds,
//        selectEnabled = selectEnabled,
//        selection = indices,
//        grabEnabled = grabEnabled,
//        seriesTooltipsEnabled = seriesTooltipsEnabled,
//        seriesTooltipsPrint = (i:Int) => sines(i).toString(),
//        axisTooltipsEnabled = axisTooltipsEnabled,
//        extraOverLayers = List(xThreshold, yThreshold)
//      )
    )

    val v = GraphSwingView(graph)

    //Zoom out by clearing manual bounds to None
    val zoomOutButton = SwingBarButton(SwingOpAction("", Some(GraphSwingView.zoomOut), SetOp(manualBounds, BoxNow(None:Option[Area]))))

    val zoomEnabledView = BooleanView.toolbar(zoomEnabled, BoxNow(Some(GraphSwingView.zoomSelect)), false)
    val selectEnabledView = BooleanView.toolbar(selectEnabled, BoxNow(Some(GraphSwingView.boxSelect)), false)
    val grabEnabledView = BooleanView.toolbar(grabEnabled, BoxNow(Some(GraphSwingView.move)), false)

    val graphProperties = SheetBuilder()
      .blankTop()
      .view("Axis Tooltips", BooleanView(axisTooltipsEnabled))
      .view("Series Tooltips", BooleanView(seriesTooltipsEnabled))
    .panel

    val settingsPopup = PopupView(icon = Some(SwingView.wrench), popupContents = graphProperties)

    val buttons = SwingButtonBar()
                    .add(selectEnabledView)
                    .add(grabEnabledView)
                    .add(zoomEnabledView)
                    .add(zoomOutButton)
                    .add(settingsPopup)
                  .buildWithListStyleComponent(EmbossedLabel("Demo Graph"))

    val panel = new JPanel(new BorderLayout())
    panel.add(v.component, BorderLayout.CENTER)

    panel.add(buttons, BorderLayout.SOUTH)

    panel
  }
  
  
  
//  def buildBarChartPanel(sines: ListVar[Sine], indices:Var[Set[Int]]) = {
//
//    val selectEnabled = Var(false)
//    val zoomEnabled = Var(true)
//    val grabEnabled = Var(false)
//    val axisTooltipsEnabled = Var(true)
//    val seriesTooltipsEnabled = Var(true)
//    val manualBounds = Var(None:Option[Area])
//    RadioReaction(selectEnabled, zoomEnabled, grabEnabled)
//    
//    //Normally we would have some intrinsic property of the displayed values that would form natural categories.
//    //In this case we really don't - it's just a list of things. Therefore we assign a single primary category "Sines"
//    //which acts as an axis title, with all bars in one group. Then we use a secondary category based on the name of the
//    //Sine. Since this may not be unique, we make a tuple of the index in the list plus the name, which we know will be
//    //unique. By providing custom print functions for the axis and tooltips, we can display just the name, ignoring the
//    //index (see withBarsSelectByKey call below)
//    val data = Cal {
//      val bars = sines().zipWithIndex.map{case (s, i) => 
////        (("Group " + i/3, s.name()), Bar(i, s.phase(), Some(s.phase()*0.9), Some(s.phase()*1.1), Some(Color.getHSBColor((9-i)/14f, 1f, 1f))))
//        (("Sines", (i, s.name())), Bar(i, s.phase(), Some(s.phase()*0.9), Some(s.phase()*1.1), Some(Color.getHSBColor((9-i)/14f, 1f, 1f))))
//      }
//      Map(bars:_*)
//    }
//    
//    import boxes.graph.Axis._
//
//    val y = Var(0.5d)
//    val yThreshold = GraphThreshold(Y, y, Color.red, "Y Threshold", true)
//
//    /*
//    //Just for demonstration purposes we set up a truly terrifying (but working) bidirectional reaction.
//    //This is NOT meant to be a robust means of mapping - in a genuine example, the barchart
//    //categories would have genuine meaning and so translating between categories and selected
//    //indices would be easier.
//    //The interesting thing here is that selection still works when more than one Sine has the same
//    //name - it just causes the same-named Sines to be selected together.
//    //The simple bit is that when indices() changes, we change selection to contain the
//    //categories we generate for the selected sines.
//    val selection = Var(Set[(String, String)]())
//    selection << {
//      val s = sines()
//      indices().map(i => ("Group " + i/3, s(i).name()))
//    }
//    //The terrifying bit is that when selection (or names of sines) change, we
//    //update the selected indices to select the indices of those sines with
//    //names matching any selected secondary categories.
//    indices << {
//      val selNames = selection().map(_._2)
//      Set(sines().zipWithIndex.flatMap{
//        case (s, i) if selNames.contains(s.name()) => Some(i)
//        case _ => None
//      }: _*)
//    }
//    
//    val graph = Var (
//      GraphBasic.withBarsSelectByCat (
//        ColorBarByCatSelection(data, selection),
//        yName = "Phase",
//        zoomEnabled = zoomEnabled,
//        manualBounds = manualBounds,
//        selectEnabled = selectEnabled,
//        selection = selection,
//        grabEnabled = grabEnabled,
//        yAxis = Val(GraphZoomerAxis(paddingBefore = 0.0, paddingAfter = 0.05)),
//        barTooltipsEnabled = seriesTooltipsEnabled,
//        axisTooltipsEnabled = axisTooltipsEnabled,
//        extraOverLayers = List(yThreshold)
//      )
//    )
//    */
//
//    //Special print code for tooltips, see data definition above
//    def tooltipsPrint(c1: String, c2: (Int, String), bar: Bar[Int]) = c2._2 + " = " + BarTooltips.printValueAndRange(bar)
//    
//    val graph = Var (
//      GraphBasic.withBarsSelectByKey (
//        ColorBarByKeySelection(data, indices),
//        cat2Print = (c2: (Int, String)) => c2._2, //Special print code for category 2 axis labels, see data definition above
//        barTooltipsPrint = tooltipsPrint, 
//        yName = "Phase",
//        zoomEnabled = zoomEnabled,
//        manualBounds = manualBounds,
//        selectEnabled = selectEnabled,
//        selection = indices,
//        grabEnabled = grabEnabled,
//        yAxis = Val(GraphZoomerAxis(paddingBefore = 0.0, paddingAfter = 0.05)),
//        barTooltipsEnabled = seriesTooltipsEnabled,
//        axisTooltipsEnabled = axisTooltipsEnabled,
//        extraOverLayers = List(yThreshold)
//      )
//    )
//
//    val v = GraphSwingBGView(graph)
//
//    //Zoom out by clearing manual bounds to None
//    val zoomOutButton = SwingBarButton(SwingOp("", Some(GraphSwingView.zoomOut), SetOp(manualBounds, None:Option[Area])))
//
//    val selectEnabledView = BooleanView(selectEnabled, "", BooleanControlType.TOOLBARBUTTON, Some(GraphSwingView.boxSelect), false)
//
//    val zoomEnabledView = BooleanView(zoomEnabled, "", BooleanControlType.TOOLBARBUTTON, Some(GraphSwingView.zoomSelect), false)
//
//    val grabEnabledView = BooleanView(grabEnabled, "", BooleanControlType.TOOLBARBUTTON, Some(GraphSwingView.move), false)
//
//    val graphProperties = SheetBuilder()
//      .blankTop()
//      .view("Axis Tooltips", BooleanView(axisTooltipsEnabled))
//      .view("Series Tooltips", BooleanView(seriesTooltipsEnabled))
//    .panel()
//
//    val settingsPopup = BoxesPopupView(icon = Some(SwingView.wrench), popupContents = graphProperties)
//
//    val buttons = SwingButtonBar()
//                    .add(selectEnabledView)
//                    .add(grabEnabledView)
//                    .add(zoomEnabledView)
//                    .add(zoomOutButton)
//                    .add(settingsPopup)
//                  .buildWithListStyleComponent(EmbossedLabel("Demo Graph"))
//
//    val panel = new JPanel(new BorderLayout())
//    panel.add(v.component, BorderLayout.CENTER)
//
//    panel.add(buttons, BorderLayout.SOUTH)
//
//    panel
//  }
  
  def properties(sine: Box[Option[Sine]])(implicit shelf: Shelf) = {

    //TODO use implicit conversion of closure to option, to remove need for PathViaOption etc.
    //The following works, but is probably worse than just having to work out and type the "ViaOption" bit
//    val f = (txn: Txn) => {implicit val t = txn; sine().map(_.name)}
//    val name = Path.now(f)

    
    val name = PathViaOption.now(implicit txn => sine().map(_.name))
    val amplitude = PathViaOption.now(implicit txn => sine().map(_.amplitude))
    val phase = PathViaOption.now(implicit txn => sine().map(_.phase))
    val enabled = PathViaOption.now(implicit txn => sine().map(_.enabled))
    val description= PathViaOption.now(implicit txn => sine().map(_.description))
    val points = PathViaOption.now(implicit txn => sine().map(_.points))
    
    val nameView = StringOptionView(name)
    val amplitudeView = NumberOptionView(amplitude)
    val phaseView = NumberOptionView(phase)
    val enabledView = BooleanOptionView(enabled)
    val descriptionView = StringOptionView(description, true)
    val pointsView = BooleanOptionView(points)
    
    SheetBuilder()
      .blankTop()
      .view("Name", nameView)
      .view("Amplitude", amplitudeView)
      .view("Phase", phaseView)
      .view("Enabled", enabledView)
      .view("Points", pointsView)
      .view("Description", descriptionView, true)
    .panel

  }

  def tabs() {

    implicit val shelf = ShelfDefault()
    
    val graphIcon = IconFactory.icon("GraphTab")
    val tableIcon = IconFactory.icon("TableTab")
    val propertiesIcon = IconFactory.icon("PropertiesTab")

    val stuff = buildLedgerMulti()
    val list = stuff._2
    
    val frame = new JFrame("Boxes UI Sine Demo")

    
    val table = stuff._1
    val graph = buildGraphPanel(stuff._2, stuff._3)
//    val barchart = buildBarChartPanel(stuff._2, stuff._3)

    val sine = stuff._4

    val p = properties(sine)
    
        val tabs = TabBuilder()
        .add(graph,       BoxNow("Graph"),  BoxNow(Some(graphIcon)))
////        .add(barchart,    "Bar Chart",  Some(graphIcon))
          .add(table, BoxNow("Table"),  BoxNow(Some(tableIcon)))
          .add(p, BoxNow("Edit"),  BoxNow(Some(propertiesIcon)))
        .panel()

    frame.add(tabs)

    frame.pack
    frame.setMinimumSize(new Dimension(650, 550))
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
    frame.setVisible(true)

  }

  def main(args: Array[String]) {
    SwingView.later{
      SwingView.nimbus()
      tabs
    }
  }

}