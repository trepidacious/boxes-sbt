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

object TransactSwingDemo {

  def main(args: Array[String]): Unit = {
    SwingView.later {
      
      SwingView.nimbox
      
      implicit val shelf = new ShelfDefault()
  
      val text = BoxNow("Initial Text")
      
      val b = BoxNow(false)
      
      val l = LabelView(text)
      
      val s = StringView(text)
      
      val bString = BoxNow("")
      bString.now << {implicit txn => ""+b()}
      
      val lb = LabelView(bString)

      
      val check = BooleanView(b, BoxNow(""), SlideCheck, BoxNow(None))
      
      val frame = new JFrame("Transact Swing Demo")
      val panel = new JPanel()
      panel.add(l.component)
      panel.add(s.component)
      panel.add(check.component)
      panel.add(lb.component)
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