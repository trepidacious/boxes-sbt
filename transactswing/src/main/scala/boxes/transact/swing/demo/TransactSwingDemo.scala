package boxes.transact.swing.demo

import boxes.transact.ShelfDefault
import boxes.transact.BoxNow
import boxes.transact.swing.LabelView
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JButton
import javax.swing.AbstractAction
import java.awt.event.ActionEvent

object TransactSwingDemo {

  def main(args: Array[String]): Unit = {
    implicit val s = new ShelfDefault()
    val text = BoxNow("Initial Text")
    
    val l = LabelView(text)
    val frame = new JFrame("Transact Swing Demo")
    val panel = new JPanel()
    panel.add(l.component)
    panel.add(new JButton(new AbstractAction("Change"){
      override def actionPerformed(e: ActionEvent) = s.transact(implicit txn => text() = text() + ".")
    }))
    
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
    frame.add(panel);
    frame.pack()
    frame.setVisible(true)
  }

}