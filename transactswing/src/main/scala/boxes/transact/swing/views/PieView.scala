package boxes.transact.swing.views

import boxes.transact._
import boxes.transact.util._
import boxes.swing.SwingView
import boxes.swing.LinkingJLabel
import boxes.swing.BoxesJTextArea
import boxes.swing.LinkingJTextField
import boxes.swing.LinkingTextEPPanel
import boxes.swing.LinkingTextJScrollPane
import java.awt.Dimension
import javax.swing.JTextField
import java.awt.event.ActionListener
import java.awt.event.ActionEvent
import java.awt.event.FocusListener
import java.awt.event.FocusEvent
import boxes.swing.LinkingJSlider
import boxes.swing.LinkingJProgressBar
import javax.swing.DefaultBoundedRangeModel
import boxes.swing.PiePainter
import boxes.swing.LinkingEPPanel
import com.explodingpixels.painter.MacWidgetsPainter
import java.awt.Component
import java.awt.Graphics2D

object PieView {
  def apply(n:Box[Double], a:Box[Double])(implicit shelf: Shelf) = new PieOptionView(n, new TConverter[Double], a, new TConverter[Double]).asInstanceOf[SwingView]
}

object PieOptionView {
  def apply(n:Box[Option[Double]], a:Box[Option[Double]])(implicit shelf: Shelf) = new PieOptionView(n, new OptionTConverter[Double], a, new OptionTConverter[Double]).asInstanceOf[SwingView]
}

private class PieOptionView[G, H](n:Box[G], c:GConverter[G, Double], a:Box[H], d:GConverter[H, Double])(implicit shelf: Shelf) extends SwingView {

  val pie = PiePainter()

  val component:LinkingEPPanel = new LinkingEPPanel(this);

  {
    component.setBackgroundPainter(new MacWidgetsPainter[Component] {
      override def paint(g:Graphics2D, t:Component, w:Int, h:Int) {
        pie.paint(g, nDisplay, w, h, aDisplay)
      }
    })
    component.setPreferredSize(new Dimension(24, 24))
    component.setMinimumSize(new Dimension(24, 24))
  }
  var nDisplay = 0d
  var aDisplay = 0d

  val view = shelf.view(implicit txn => {
    //Store the values for later use on Swing Thread
    val newN = n()
    val newA = a()
    //This will be called from Swing Thread
    replaceUpdate {
      nDisplay = c.toOption(newN).getOrElse(0d)
      aDisplay = d.toOption(newA).getOrElse(0d)
      component.repaint()
    }
  })
}


