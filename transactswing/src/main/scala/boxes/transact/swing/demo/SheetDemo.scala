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
import boxes.transact.Box
import boxes.transact.Shelf
import boxes.transact.swing.layout.SheetBuilder

object SheetDemo {

  class Sine(implicit txn: Txn) {
    val name = Box("Sine")
    val phase = Box(0d)
    val amplitude = Box(1d)
    val enabled = Box(true)
    val points = Box(false)
    val description = Box("Default Description\nCan have multiple lines")
  }
  
  object Sine {
    def apply(implicit txn: Txn) = new Sine
    def now(implicit shelf: Shelf) = shelf.transact(implicit txn => new Sine)
  }
  
  def main(args: Array[String]): Unit = {
    SwingView.later {
      
      val format = new DecimalFormat("0.00")
      
      SwingView.nimbus
      
      implicit val shelf = new ShelfDefault()

      val s = Sine.now

      val nameView = StringView(s.name)
      
      val amplitudeView = NumberView(s.amplitude)
      val phaseView = NumberView(s.phase)
//      val enabledView = BooleanView(s.enabled)
      val descriptionView = StringView(s.description, true)
//      val pointsView = BooleanOptionView(s.points)

      val sheet = SheetBuilder()
      val properties = sheet
                      .separator("Edit Sine")
                      .view("Name", nameView)
                      .view("Amplitude", amplitudeView)
                      .view("Phase", phaseView)
//                      .view("Enabled", enabledView)
//                      .view("Points", pointsView)
                      .view("Description", descriptionView, true)
                     .panel
      
      val frame = new JFrame("Transact Swing Sheet Demo")
//      val panel = new JPanel()
//      panel.add(properties)
      
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
      frame.add(properties);
      frame.pack()
      frame.setVisible(true)
    }

  }

}