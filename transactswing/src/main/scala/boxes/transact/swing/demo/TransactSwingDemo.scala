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
      
      
      val bo = BoxNow(false)
      val bString = BoxNow.calc{implicit txn => ""+bo()}
      
      val check = BooleanView(bo, BoxNow(""), SlideCheck, BoxNow(None))
      
      val lb = LabelView(bString)

      val i = BoxNow(1)
      val iString = BoxNow.calc{implicit txn => ""+i()}
      val iLabel = LabelView(iString)
      val iSlider = RangeView(i, 1, 100)
      
      
      val dub = BoxNow(1.0d)
      val dString = BoxNow.calc{implicit txn => format.format(dub())}
      val dLabel = LabelView(dString)
      val dPie = PieView(dub, BoxNow(1.0d))
      val dSpinner = NumberView(dub)
      
      val a = BoxNow(20d)
      val b = BoxNow(20d)
      val c = BoxNow(20d)
      val d = BoxNow(20d)
      val e = BoxNow(20d)
      val all = Set(a, b, c, d, e)
      
      shelf.transact(implicit txn => {
        val r = txn.createReaction(implicit r => {
          all.foreach(_())
          val done = all.find(box => {
            if (r.changedSources == Set(box)) {
              val sum = all.toSeq.map(_()).sum
              val adjust = (100d - sum)/(all.size - 1)
              all.-(box).foreach(oBox => oBox() = oBox() + adjust)
              true
            } else {
              false
            }
          }).isDefined
          //If no individual box was changed without the others changing,
          //just adjust all together
          if (!done) {
            val sum = all.toSeq.map(_()).sum
              val adjust = (100d - sum)/(all.size)
              all.foreach(oBox => oBox() = oBox() + adjust)
          }
        })
        a.retainReaction(r)
      })
      
      
      
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
      all.foreach(box => panel.add(NumberView(box).component))
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