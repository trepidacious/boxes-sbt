package boxes.transact.swing.views

import boxes.transact._
import boxes.swing.SwingView
import boxes.swing.LinkingJLabel
import boxes.transact.swing.TSwingView
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

object RangeView {
  def apply(v:Box[Int], min:Int, max:Int, progress:Boolean = false)(implicit shelf: Shelf) = new RangeOptionView(v, min, max, new TConverter[Int], progress).asInstanceOf[SwingView]
}

object RangeOptionView {
  def apply(v:Box[Option[Int]], min:Int, max:Int, progress:Boolean = false)(implicit shelf: Shelf) = new RangeOptionView(v, min, max, new OptionTConverter[Int], progress).asInstanceOf[SwingView]
}

private class RangeOptionView[G](v:Box[G], min:Int, max:Int, c:GConverter[G, Int], progress:Boolean)(implicit shelf: Shelf) extends SwingView {

  private val model = new AutoBoundedRangeModel(min, max)
  val component = if (!progress) new LinkingJSlider(this, model) else new LinkingJProgressBar(this, model)

  val view = TSwingView.swingView(implicit txn => {
    //Store the values for later use on Swing Thread
    val newV = v()
    //This will be called from Swing Thread
    replaceUpdate {
      c.toOption(newV) match {
        case None => {
          component.setEnabled(false)
          model.fireNewValue(model.getMinimum)
        }
        case Some(i) => {
          component.setEnabled(true)
          model.fireNewValue(i)
        }
      }
    }
  })

  private class AutoBoundedRangeModel(min:Int, max:Int)(implicit shelf: Shelf) extends DefaultBoundedRangeModel(min, 0, min, max) {

    private var currentValue = 0

    def fireNewValue(i:Int) = {
      //If necessary, extend range to cover value we have seen
      if (i < getMinimum) setMinimum(i)
      if (i > getMaximum) setMaximum(i)
      currentValue = i

      fireStateChanged
    }

    override def getValue = currentValue

    override def getExtent = 0

    override def setValue(n:Int) = currentValue = n

    override def setValueIsAdjusting(b:Boolean) = {
      super.setValueIsAdjusting(b)
      //TODO why do we need this?
      shelf.transact(implicit t => {
        c.toOption(v()) match {
          case None => {}
          case Some(_) => v() = c.toG(currentValue)
        }
      })
    }

  }

}


