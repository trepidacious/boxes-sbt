package boxes.transact.swing.graph

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

object GraphSwingDemo {

  def main(args: Array[String]): Unit = {
    SwingView.later {
      SwingView.nimbus
      implicit val shelf = new ShelfDefault()

      val series = new Series("Key", List(Vec2(0,0), Vec2(1,1)))
      
      val graph = GraphBasic.withSeries[String](
          BoxNow(List(series)),             //series
          BoxNow("x"),                      //xName
          BoxNow("y"),                      //yName
          BoxNow(Borders(16, 74, 53, 16)),  //borders
          BoxNow(false),                     //zoomEnabled
          BoxNow(None),                     //manualBounds
          BoxNow(GraphZoomerAxis()),        //xAxis
          BoxNow(GraphZoomerAxis()),        //yAxis
          BoxNow(false),                     //selectEnabled
          BoxNow(false),                     //clickSelectEnabled
          BoxNow(Set("Key")),               //selection
          BoxNow(true),                     //grabEnabled
          BoxNow(false),                     //seriesTooltipsEnabled
          (s: String) => s,                 //seriesTooltipsPrint
          BoxNow(false),                     //axisTooltipsEnabled
          Nil,                              //extraMainLayers
          Nil,                              //extraOverLayers
          BoxNow(true),                     //highQuality
          SwingView.background,             //border
          Color.white)                      //background
      
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