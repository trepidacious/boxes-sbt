package boxes.transact.graph

import boxes.graph.Series
import boxes.transact.Box
import boxes.transact.BoxNow
import boxes.transact.Shelf

object ColorSeriesBySelection {
  def defaultSeriesToUnselected[K](s: Series[K]) = s.copy(color = GraphSeries.blendColors(s.color, GraphSeries.unselectedColor, 0.4), width = 1, shadow = false)
    
  def apply[K](series:Box[List[Series[K]]], indices:Box[Set[K]], seriesToUnselected: (Series[K] => Series[K]) = defaultSeriesToUnselected[K] _)(implicit shelf: Shelf) = 
    BoxNow.calc(implicit txn =>{
      val unselected = series().collect{
        case s:Series[K] if !indices().contains(s.key) => seriesToUnselected(s)
      }
      val selected = series().filter(s => indices().contains(s.key))
  
      unselected ::: selected
    })
}
