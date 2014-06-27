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
      
      SwingView.nimbus
      
      implicit val shelf = new ShelfDefault()
  
      val text = BoxNow("Initial Text")
      
      
      val l = LabelView(text)
      
      val s = StringView(text)
      
      
      val bo = BoxNow(false)
      val bString = BoxNow.calc{implicit txn => ""+bo()}
      
      val check = BooleanView(bo, SlideCheck)
      
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
      val requiredTotal = BoxNow(100)
      val all = Set(a, b, c, d, e)
      
      shelf.transact(implicit txn => {
        val r = txn.createReaction(implicit r => {
          println("Reacting")
          //Sum all boxes first - this ensures we react to changes
          //in any of them, since we have read them
          val sum = all.toSeq.map(_()).sum
          val t = requiredTotal() 
          
          if (Math.abs(sum - t) > 0.0001) {
          
            //See if one box was changed first - if so preserve its value
            val done = all.find(box => {
              if (r.changedSources == Set(box)) {
                val adjust = (t - sum)/(all.size - 1)
                all.-(box).foreach(oBox => oBox() = oBox() + adjust)
                true
              } else {
                false
              }
            }).isDefined
            
            //Otherwise adjust all boxes equally
            if (!done) {
              val adjust = (t - sum)/(all.size)
              all.foreach(oBox => oBox() = oBox() + adjust)
            }
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
      panel.add(NumberView(requiredTotal).component)
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