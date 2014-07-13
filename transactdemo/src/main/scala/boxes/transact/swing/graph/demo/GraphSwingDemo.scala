package boxes.transact.swing.graph.demo

import boxes.swing.SwingView
import boxes.transact.ShelfDefault
import boxes.transact.graph.GraphBasic
import boxes.graph.Series
import boxes.graph.Vec2
import boxes.transact.BoxNow
import boxes.graph.Borders
import boxes.graph.GraphZoomerAxis
import java.awt.Color
import javax.swing.JFrame
import java.awt.Dimension
import boxes.transact.swing.graph.GraphSwingView
import boxes.transact.TxnR
import boxes.transact.graph.StringTooltipPrinter
import boxes.transact.graph.GraphDefaults
import boxes.transact.graph.Charts

object GraphSwingDemo {

  def main(args: Array[String]): Unit = {
    SwingView.later {
      SwingView.nimbus
      implicit val shelf = new ShelfDefault()

      val series = new Series("Key", List(Vec2(0,0), Vec2(1,1)))
      
      val charts = Charts()
      val graph = charts.withSeries(
          series = BoxNow(List(series)),
          zoomEnabled = BoxNow(false),
          selection = BoxNow(Set("Key")),
          grabEnabled = BoxNow(true)
      )
      
      val graphBox = BoxNow(graph)
      val view = GraphSwingView(graphBox)
      
      val frame = new JFrame("Transact Swing Demo")

      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
      view.component.setMinimumSize(new Dimension(400, 400))
      view.component.setPreferredSize(new Dimension(400, 400))
      view.component.setSize(new Dimension(400, 400))
      frame.add(view.component);
      frame.pack()
      frame.setVisible(true)
    }
  }

}