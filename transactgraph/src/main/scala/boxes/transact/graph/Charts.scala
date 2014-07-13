package boxes.transact.graph

import java.awt.Color

import boxes.graph.Area
import boxes.graph.Axis.X
import boxes.graph.Axis.Y
import boxes.graph.Borders
import boxes.graph.Series
import boxes.swing.SwingView
import boxes.transact.Box
import boxes.transact.BoxNow
import boxes.transact.Shelf

object Charts {
  def apply()(implicit shelf: Shelf) = new Charts
}

class Charts(implicit val shelf: Shelf) {
  
  def withSeries[K](
      series: Box[List[Series[K]]],
      xName: Box[String] = BoxNow("x"),
      yName: Box[String] = BoxNow("y"),
      borders: Box[Borders] = BoxNow(Borders(16, 74, 53, 16)),
      zoomEnabled: Box[Boolean] = BoxNow(false),
      manualBounds: Box[Option[Area]] = BoxNow(None:Option[Area]),
      xAxis: Box[GraphZoomerAxis] = BoxNow(GraphDefaults.axis),
      yAxis: Box[GraphZoomerAxis] = BoxNow(GraphDefaults.axis),
      selectEnabled: Box[Boolean] = BoxNow(false),
      clickSelectEnabled: Box[Boolean] = BoxNow(true),
      selection: Box[Set[K]] = BoxNow(Set.empty),
      grabEnabled: Box[Boolean] = BoxNow(false),
      seriesTooltipsEnabled: Box[Boolean] = BoxNow(true),
      seriesTooltipsPrinter: TooltipPrinter[K] = new StringTooltipPrinter[K](),
      axisTooltipsEnabled: Box[Boolean] = BoxNow(true),
      extraMainLayers: List[GraphLayer] = Nil,
      extraOverLayers: List[GraphLayer] = Nil,
      highQuality: Box[Boolean] = BoxNow(true),
      border: Color = SwingView.background,
      background: Color = Color.white
      ) = {

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
        List(SeriesTooltips.highlight(series, seriesTooltipsEnabled)) ::: extraOverLayers ::: List(
        GraphZoomBox(BoxNow(new Color(0, 0, 200, 50)), BoxNow(new Color(100, 100, 200)), manualBounds, zoomEnabled),
        GraphSelectBox(series, BoxNow(new Color(0, 200, 0, 50)), BoxNow(new Color(100, 200, 100)), selection, selectEnabled),
        GraphGrab(grabEnabled, manualBounds, zoomer.dataArea),
        GraphClickToSelectSeries(series, selection, clickSelectEnabled),
        AxisTooltip(X, axisTooltipsEnabled),
        AxisTooltip(Y, axisTooltipsEnabled),
        SeriesTooltips.string(series, seriesTooltipsEnabled, seriesTooltipsPrinter)
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
  
}