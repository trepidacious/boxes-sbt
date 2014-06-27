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
import javax.swing.Icon
import boxes.swing.LinkingJCheckBox
import boxes.swing.LinkingJRadioButton
import boxes.swing.LinkingJToggleButton
import boxes.swing.LinkingToolbarToggleButton
import boxes.swing.LinkingSlideCheckButton
import boxes.swing.LinkingTabButton
import javax.swing.JToggleButton.ToggleButtonModel

sealed trait BooleanControlType
case object Checkbox extends BooleanControlType
case object ToggleButton extends BooleanControlType
case object ToolbarButton extends BooleanControlType
case object SlideCheck extends BooleanControlType
case object Radio extends BooleanControlType
case object Tab extends BooleanControlType

object BooleanView {
  def extended(v:Box[Boolean], n:Box[String], controlType:BooleanControlType = SlideCheck, icon:Box[Option[Icon]], toggle:Boolean = true)(implicit shelf: Shelf) = new BooleanOptionView(v, n, new TConverter[Boolean], controlType, icon, toggle).asInstanceOf[SwingView]
  def apply(v:Box[Boolean], controlType:BooleanControlType = SlideCheck, toggle:Boolean = true)(implicit shelf: Shelf) = new BooleanOptionView(v, BoxNow(""), new TConverter[Boolean], controlType, BoxNow(None), toggle).asInstanceOf[SwingView]
}

object BooleanOptionView {
  def extended(v:Box[Option[Boolean]], n:Box[String], controlType:BooleanControlType = SlideCheck, icon:Box[Option[Icon]], toggle:Boolean = true)(implicit shelf: Shelf) = new BooleanOptionView(v, n, new OptionTConverter[Boolean], controlType, icon, toggle).asInstanceOf[SwingView]
  def apply(v:Box[Option[Boolean]], controlType:BooleanControlType = SlideCheck, toggle:Boolean = true)(implicit shelf: Shelf) = new BooleanOptionView(v, BoxNow(""), new OptionTConverter[Boolean], controlType, BoxNow(None), toggle).asInstanceOf[SwingView]
}

private class BooleanOptionView[G](v:Box[G], n:Box[String], c:GConverter[G, Boolean], controlType:BooleanControlType, icon:Box[Option[Icon]], toggle:Boolean = true)(implicit shelf: Shelf) extends SwingView {

  val component = controlType match {
    case Checkbox => new LinkingJCheckBox(this)
    case Radio => new LinkingJRadioButton(this)
    case ToggleButton => new LinkingJToggleButton(this)
    case ToolbarButton => new LinkingToolbarToggleButton(this)
    case SlideCheck => new LinkingSlideCheckButton(this)
    case Tab => new LinkingTabButton(this)
  }

  private val model = new AutoButtonModel()

  {
    component.setModel(model)
    component.addActionListener(new ActionListener(){
      //On action, toggle value if it is not None
      override def actionPerformed(e:ActionEvent) = shelf.transact(implicit txn => {
        c.toOption(v()) match {
          case None => None
          case Some(b) => v() = if (toggle) c.toG(!b) else c.toG(true)
        }
      })
    })
  }

  val view = shelf.view(implicit txn => {
    //Store the values for later use on Swing Thread
    val newV = v()
    val newN = n()
    val newIcon = icon()
    //This will be called from Swing Thread
    replaceUpdate { display(newV, newN, newIcon) }
  })

  //Update display if necessary
  private def display(newV:G, newN:String, newIcon:Option[Icon]) {
    c.toOption(newV) match {
      case None => {
        model.enabled = false
        model.selected = false
      }
      case Some(b) => {
        model.enabled = true
        model.selected = b
      }
    }
    model.fire
    if (newN != component.getText) {
      component.setText(newN)
    }
    val iconOrNull = newIcon.getOrElse(null)
    if (iconOrNull != component.getIcon) {
      component.setIcon(iconOrNull)
    }
  }

  private class AutoButtonModel extends ToggleButtonModel {
    var enabled = true
    var selected = true
    def fire() = fireStateChanged()
    override def isSelected = selected
    override def isEnabled = enabled
  }

}

