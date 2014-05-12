package boxes.transact.swing

import boxes.transact._
import boxes.swing.LinkingJLabel
import boxes.swing.SwingView
import boxes.transact.Box
import boxes.transact.Shelf
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executor
import javax.swing.SwingUtilities

object SwingExecutorService extends Executor {
  override def execute(command: Runnable) = SwingUtilities.invokeLater(command)
}

object TSwingView {
  def swingView(f: TxnR => Unit)(implicit shelf: Shelf) = {
    shelf.view(f, SwingExecutorService, true)
  }
}

//TODO use a renderer to customise display
private class LabelOptionView[G](v:Box[G], c:GConverter[G, String])(implicit shelf: Shelf) extends SwingView {

  val component = new LinkingJLabel(this)

  val view = TSwingView.swingView(implicit txn => {
    //Store the value for later use on Swing Thread
    val newV = v()
    //This will be called from Swing Thread
    replaceUpdate {display(newV)}
  })

  //Update display if necessary
  private def display(s:G) {
    val text = c.toOption(s) match {
      case None => ""
      case Some(string) => string
    }
    if (!component.getText.equals(text)) {
      component.setText(text)
    }
  }
}

object LabelView {
  def apply(v:Box[String])(implicit shelf: Shelf) = new LabelOptionView(v, new TConverter[String]).asInstanceOf[SwingView]
}

object LabelOptionView {
  def apply(v:Box[Option[String]])(implicit shelf: Shelf) = new LabelOptionView(v, new OptionTConverter[String]).asInstanceOf[SwingView]
}
