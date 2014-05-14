package boxes.transact.swing.demo

import boxes.transact.ShelfDefault
import boxes.transact.BoxNow
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JButton
import javax.swing.AbstractAction
import java.awt.event.ActionEvent
import boxes.transact.swing.views._
import javax.swing.SwingUtilities
import boxes.swing.SwingView
import boxes.transact.Txn
import java.text.DecimalFormat

object TransactSwingDemo {

  def main(args: Array[String]): Unit = {
    SwingView.later {
      
      val format = new DecimalFormat("0.00")
      
      SwingView.nimbox
      
      implicit val shelf = new ShelfDefault()
  
      val text = BoxNow("Initial Text")
      
      
      val l = LabelView(text)
      
      val s = StringView(text)
      
      
      val b = BoxNow(false)
      val bString = BoxNow.calc{implicit txn => ""+b()}
      
      val check = BooleanView(b, BoxNow(""), SlideCheck, BoxNow(None))
      
      val lb = LabelView(bString)

      val i = BoxNow(1)
      val iString = BoxNow.calc{implicit txn => ""+i()}
      val iLabel = LabelView(iString)
      val iSlider = RangeView(i, 1, 100)
      
      
      val d = BoxNow(1.0d)
      val dString = BoxNow.calc{implicit txn => format.format(d())}
      val dLabel = LabelView(dString)
      val dPie = PieView(d, BoxNow(1.0d))
      val dSpinner = NumberView(d)
      
      val frame = new JFrame("Transact Swing Demo")
      val panel = new JPanel()
      panel.add(l.component)
      panel.add(s.component)
      panel.add(check.component)
      panel.add(lb.component)
      panel.add(iLabel.component)
      panel.add(iSlider.component)
      panel.add(dLabel.component)
      panel.add(dPie.component)
      panel.add(dSpinner.component)
      panel.add(new JButton(new AbstractAction("Change"){
        override def actionPerformed(e: ActionEvent) = shelf.transact(implicit txn => text() = text() + ".")
      }))
      
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
      frame.add(panel);
      frame.pack()
      frame.setVisible(true)
    }

  }

}