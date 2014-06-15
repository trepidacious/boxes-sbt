package boxes.transact.swing.op

import boxes.transact.op._
import boxes.transact.Box
import java.awt.Image
import javax.swing.Icon
import javax.swing.AbstractAction
import java.awt.event.ActionEvent
import boxes.transact.Shelf
import boxes.swing.SwingView
import javax.swing.Action
import boxes.swing.icons.IconFactory
import com.explodingpixels.swingx.EPPanel
import boxes.swing.BarStylePainter
import java.awt.Component
import javax.swing.JComponent
import javax.swing.border.EmptyBorder
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.BoxLayout
import boxes.transact.data._
import boxes.transact.BoxNow
import com.explodingpixels.swingx.EPButton
import boxes.swing.SwingBarButtonDefault
import boxes.swing.SwingButtonDefault
import boxes.transact.op.ListAddOp

//trait SwingOp extends Op {
//  def icon: Box[Option[Image]]
//  def label: Box[String]
//}
//
//private class SwingOpDefault(action: => Unit, val canApply:Box[Boolean], val icon:Box[Option[Image]], val label:Box[String]) extends Op {
//  def apply() = action
//}
//
//object SwingOp {
//  def apply(action: => Unit, canApply:Box[Boolean], icon:Box[Option[Image]], label:Box[String]) = new SwingOpDefault(action, canApply, icon, label): Op
//}

class SwingOpAction(name: Box[String], icon: Box[Option[Icon]], op:Op)(implicit shelf: Shelf) extends AbstractAction(name.now(), icon.now().getOrElse(null)) {

  def actionPerformed(e:ActionEvent) = op()
  
  val view = shelf.view(implicit txn => {
    val enabled = op.canApply()
    SwingView.replaceUpdate(this, setEnabled(enabled))
  })
  
  val viewName = shelf.view(implicit txn => {
    val nameNow = name()
    SwingView.replaceUpdate(this, putValue(Action.NAME, nameNow))
  })

  val viewIcon = shelf.view(implicit txn => {
    val iconNow = icon()
    SwingView.replaceUpdate(this, putValue(Action.SMALL_ICON, iconNow.getOrElse(null)))
  })
}

object SwingOpAction {

  val add = Some(IconFactory.icon("Plus"))
  val delete = Some(IconFactory.icon("Minus"))
  val up = Some(IconFactory.icon("Up"))
  val down = Some(IconFactory.icon("Down"))

  def apply(name: String = "", icon: Option[Icon] = None, op: Op)(implicit shelf: Shelf): SwingOpAction = new SwingOpAction(BoxNow(name), BoxNow(icon), op)
  def apply(name: Box[String], icon: Box[Option[Icon]], op: Op)(implicit shelf: Shelf): SwingOpAction = new SwingOpAction(name, icon, op)

  def apply(op:Op)(implicit shelf: Shelf): SwingOpAction = {
    op match {
      case o:ListAddOp[_] => SwingOpAction("", add, op)
      case o:ListMultiAddOp[_] => SwingOpAction("", add, op)
      case o:ListDeleteOp[_] => SwingOpAction("", delete, op)
      case o:ListMultiDeleteOp[_] => SwingOpAction("", delete, op)
      case o:ListMoveOp[_] => {
        if (o.up) {
          SwingOpAction("", up, op)
        } else {
          SwingOpAction("", down, op)
        }
      }
      case o:ListMultiMoveOp[_] => {
        if (o.up) {
          SwingOpAction("", up, op)
        } else {
          SwingOpAction("", down, op)
        }
      }
      //FIXME use implicits
      case _ => throw new IllegalArgumentException("Unknown op")
    }
  }

}

object SwingBarPadding {
  def apply() = {
    val panel = new EPPanel()
    panel.setBackgroundPainter(BarStylePainter[Component](false, false))
    panel
  }
}

object SwingBarButton {
  def apply(name:String, icon:Option[Icon] = None, op:Op)(implicit shelf: Shelf): EPButton = {
    apply(SwingOpAction(name, icon, op))
  }
  
  def apply(op:Op)(implicit shelf: Shelf): EPButton = {
    val s = SwingOpAction(op)
    apply(s)
  }

  def apply(a:Action) = new SwingBarButtonDefault(a)
}

object SwingButton {
  def apply(name:String, icon:Option[Icon] = None, op:Op)(implicit shelf: Shelf): EPButton = new SwingButtonDefault(SwingOpAction(name, icon, op))
  def apply(op:Op)(implicit shelf: Shelf): EPButton = new SwingButtonDefault(SwingOpAction(op))
  def apply(op:SwingOpAction):EPButton = new SwingButtonDefault(op)
}

object SwingButtonBar {
  def apply() = new SwingButtonBarBuilder(List[JComponent]())
}

class SwingButtonBarBuilder(val components:List[JComponent]) {
  def add(c:JComponent) = new SwingButtonBarBuilder(components ::: List(c))
  def add(op:Op)(implicit shelf: Shelf): SwingButtonBarBuilder = add(SwingBarButton(op))
  def add(v:SwingView) = new SwingButtonBarBuilder(components ::: List(v.component))

  def addComponent(c:Option[JComponent]) = c.map(cv => new SwingButtonBarBuilder(components ::: List(cv))).getOrElse(this)
  def addOp(op:Option[Op])(implicit shelf: Shelf): SwingButtonBarBuilder = addComponent(op.map(SwingBarButton(_)))
  def addSwingView(v:Option[SwingView]): SwingButtonBarBuilder = addComponent(v.map(_.component))

  def buildWithListStyleComponent(c:JComponent) = {
    val padding = SwingBarPadding()
    padding.setBorder(new EmptyBorder(0, 5, 0, 5))
    padding.setLayout(new BorderLayout)
    padding.add(c)
    c.setOpaque(false)
    build(padding)
  }

  def build(padding:JComponent = SwingBarPadding()) = {
    val buttonPanel = new JPanel()
    buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS))
    components.foreach(c => buttonPanel.add(c))

    val bottom = new JPanel(new BorderLayout())
    bottom.add(buttonPanel, BorderLayout.WEST)
    bottom.add(padding, BorderLayout.CENTER)

    bottom
  }
}




