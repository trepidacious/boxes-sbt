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
import boxes.graph.Bar

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
  
  
  def withBarsSelectByCat[C1, C2, K](
      data: Box[Map[(C1, C2), Bar[K]]],
      cat1Print: (C1 => String) = (c: C1) => c.toString, 
      cat2Print: (C2 => String) = (c: C2) => c.toString,
      barWidth: Box[Double] = BoxNow(1.0), 
      catPadding: Box[Double] = BoxNow(1.0), 
      barPadding: Box[Double] = BoxNow(0.4),
      yName: Box[String] = BoxNow("y"),
      borders: Box[Borders] = BoxNow(Borders(16, 74, 53, 16)),
      zoomEnabled: Box[Boolean] = BoxNow(true),
      manualBounds: Box[Option[Area]] = BoxNow(None),
      xAxis: Box[GraphZoomerAxis] = BoxNow(GraphDefaults.axis),
      yAxis: Box[GraphZoomerAxis] = BoxNow(GraphDefaults.axis(0, 0.05)),
      selectEnabled: Box[Boolean] = BoxNow(false),
      clickSelectEnabled: Box[Boolean] = BoxNow(true),
      selection:Box[Set[(C1, C2)]],
      grabEnabled: Box[Boolean] = BoxNow(false),
      barTooltipsEnabled: Box[Boolean] = BoxNow(true),
      barTooltipsPrint: ((C1, C2, Bar[K]) => String) = BarTooltips.defaultPrint[C1, C2, K],
      axisTooltipsEnabled: Box[Boolean] = BoxNow(true),
      extraMainLayers:List[GraphLayer] = List[GraphLayer](),
      extraOverLayers:List[GraphLayer] = List[GraphLayer](),
      highQuality: Box[Boolean] = BoxNow(true)
      )(implicit shelf: Shelf, ord1: Ordering[C1], ord2: Ordering[C2]) = {

    val layers = BoxNow(
      extraMainLayers ::: List(
        new GraphBG(SwingView.background, Color.white),
        new GraphHighlight(),
        new GraphBars(data, barWidth, catPadding, barPadding, true),  //Shadows
        new GraphAxis(Y, 50),
        new GraphBarAxis(data, barWidth, catPadding, barPadding, X, cat1Print, cat2Print),
        new GraphShadow(),
        new GraphBars(data, barWidth, catPadding, barPadding, false), //Data
        new GraphOutline(),
        new GraphAxisTitle(Y, yName)
      )
    )

    val dataBounds = BoxNow.calc(implicit TxnR => {
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
//      List(SeriesTooltips.highlight(series, seriesTooltipsEnabled)) ::: 
      extraOverLayers ::: List(
        GraphZoomBox(BoxNow(new Color(0, 0, 200, 50)), BoxNow(new Color(100, 100, 200)), manualBounds, zoomEnabled),
        GraphSelectBarsByCatWithBox(data, selection, barWidth, catPadding, barPadding, selectEnabled, BoxNow(new Color(0, 200, 0, 50)), BoxNow(new Color(100, 200, 100))),
        GraphGrab(grabEnabled, manualBounds, zoomer.dataArea),
        GraphClickToSelectBarByCat(data, selection, barWidth, catPadding, barPadding, clickSelectEnabled),
        AxisTooltip(Y, axisTooltipsEnabled),
        BarTooltips.string(barTooltipsEnabled, data, barWidth, catPadding, barPadding, barTooltipsPrint)
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
 
  def withBarsSelectByKey[C1, C2, K](
      
      data: Box[Map[(C1, C2), Bar[K]]],
      cat1Print: (C1 => String) = (c: C1) => c.toString, 
      cat2Print: (C2 => String) = (c: C2) => c.toString,
      barWidth: Box[Double] = BoxNow(1.0), 
      catPadding: Box[Double] = BoxNow(1.0), 
      barPadding: Box[Double] = BoxNow(0.4),
      yName: Box[String] = BoxNow("y"),
      borders: Box[Borders] = BoxNow(Borders(16, 74, 53, 16)),
      zoomEnabled: Box[Boolean] = BoxNow(true),
      manualBounds: Box[Option[Area]] = BoxNow(None),
      xAxis: Box[GraphZoomerAxis] = BoxNow(GraphDefaults.axis),
      yAxis: Box[GraphZoomerAxis] = BoxNow(GraphDefaults.axis(0, 0.05)),
      selectEnabled: Box[Boolean] = BoxNow(false),
      clickSelectEnabled: Box[Boolean] = BoxNow(true),
      selection: Box[Set[K]],
      grabEnabled: Box[Boolean] = BoxNow(false),
      barTooltipsEnabled: Box[Boolean] = BoxNow(true),
      barTooltipsPrint: ((C1, C2, Bar[K]) => String) = BarTooltips.defaultPrint[C1, C2, K],
      axisTooltipsEnabled: Box[Boolean] = BoxNow(true),
      extraMainLayers:List[GraphLayer] = List[GraphLayer](),
      extraOverLayers:List[GraphLayer] = List[GraphLayer](),
      highQuality: Box[Boolean] = BoxNow(true)      
      )(implicit shelf: Shelf, ord1: Ordering[C1], ord2: Ordering[C2]) = {

    val layers = BoxNow(
      extraMainLayers ::: List(
        new GraphBG(SwingView.background, Color.white),
        new GraphHighlight(),
        new GraphBars(data, barWidth, catPadding, barPadding, true),  //Shadows
        new GraphAxis(Y, 50),
        new GraphBarAxis(data, barWidth, catPadding, barPadding, X, cat1Print, cat2Print),
        new GraphShadow(),
        new GraphBars(data, barWidth, catPadding, barPadding, false), //Data
        new GraphOutline(),
        new GraphAxisTitle(Y, yName)
      )
    )

    val dataBounds = BoxNow.calc(implicit TxnR => {
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
//      List(SeriesTooltips.highlight(series, seriesTooltipsEnabled)) ::: 
      extraOverLayers ::: List(
        GraphZoomBox(BoxNow(new Color(0, 0, 200, 50)), BoxNow(new Color(100, 100, 200)), manualBounds, zoomEnabled),
        GraphSelectBarsByKeyWithBox(data, selection, barWidth, catPadding, barPadding, selectEnabled, BoxNow(new Color(0, 200, 0, 50)), BoxNow(new Color(100, 200, 100))),
        GraphGrab(grabEnabled, manualBounds, zoomer.dataArea),
        GraphClickToSelectBarByKey(data, selection, barWidth, catPadding, barPadding, clickSelectEnabled),
        AxisTooltip(Y, axisTooltipsEnabled),
        BarTooltips.string(barTooltipsEnabled, data, barWidth, catPadding, barPadding, barTooltipsPrint)
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