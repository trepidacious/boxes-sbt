package boxes.transact.swing

import boxes.transact._
import boxes.swing.LinkingJLabel
import boxes.swing.SwingView
import boxes.transact.Box
import boxes.transact.Shelf
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executor
import javax.swing.SwingUtilities

object SwingExecutor extends Executor {
  override def execute(command: Runnable) = SwingUtilities.invokeLater(command)
}

//object TSwingView {
//  def swingView(f: TxnR => Unit)(implicit shelf: Shelf) = {
//    shelf.view(f, SwingExecutor, true)
//  }
//}
