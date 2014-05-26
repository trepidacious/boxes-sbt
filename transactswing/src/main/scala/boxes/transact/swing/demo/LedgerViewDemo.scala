package boxes.transact.swing.demo

import boxes.transact._
import boxes.transact.data._
import boxes.transact.swing.views.LedgerView
import javax.swing.JFrame
import javax.swing.JScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import boxes.transact.swing.views.NumberOptionView
import boxes.util.Step
import javax.swing.JButton
import javax.swing.AbstractAction
import java.awt.event.ActionEvent
import javax.swing.JPanel
import boxes.swing.SwingView

object LedgerViewDemo {
  
  class Person(
      val name: Box[String], 
      val age: Box[Int],
      val friend: Box[Option[Person]])
      
  object Person {
    def apply()(implicit s: Shelf) = s.transact(implicit txn => new Person(Box("Unnamed"), Box(20), Box(None)))
  }
  
  def ledger() {
    
    implicit val s = ShelfDefault()

    val p = Person()
    p.name.now() = "p"
    val q = Person()
    q.name.now() = "q"

    val list = BoxNow(List(p, q, q, p))

    val view = LensRecordView[Person](
      MBoxLens("Name", _.name),
      MBoxLens("Age", _.age)
    )
    
    val ledger = s.transact(implicit txn => ListLedgerBox(list, view))

//    s.read(implicit txn => {
//      for (f <- 0 until ledger().fieldCount) {
//        print(ledger().fieldName(f) + "\t")
//      }
//      println()
//      for (f <- 0 until ledger().fieldCount) {
//        print(ledger().fieldClass(f) + "\t")
//      }
//      println()
//      for (r <- 0 until ledger().recordCount) {
//        for (f <- 0 until ledger().fieldCount) {
//          print(ledger().apply(r, f) + "\t")
//        }
//        println()
//      }      
//    })

//    val ledgerView = LedgerView.singleSelection(ledger, index)
//    val ledgerView = new LedgerView(ledger)
    val i = BoxNow(Some(0): Option[Int])
    val ledgerView = LedgerView.singleSelectionScroll(ledger, i, true)
    
    val indexView = NumberOptionView(i, Step(1))

    val add = new JButton(new AbstractAction("Add") {
      override def actionPerformed(e:ActionEvent) = {
        val person = Person()
        s.transact(implicit txn => {
          person.name() = "New item at " + list().size
          list() = list() ++ List(person)
        })
        
      }
    })

    val delete = new JButton(new AbstractAction("Delete") {
      override def actionPerformed(e:ActionEvent) = {
        s.transact(implicit txn => {
          if (!list().isEmpty) list() = list().tail          
        })
      }
    })

    val frame = new JFrame()
    val panel = new JPanel()
    panel.add(add)
    panel.add(delete)
    panel.add(indexView.component)
    frame.add(ledgerView.component, BorderLayout.CENTER)
    frame.add(panel, BorderLayout.SOUTH)
    frame.pack
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
    frame.setVisible(true)

  }
  
  def main(args: Array[String]) {
    SwingView.later{
      SwingView.nimbus
      ledger
    }
  }
}