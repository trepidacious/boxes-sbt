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
import boxes.transact.swing.views.LabelView
import scala.collection.immutable._

object LedgerViewIndicesDemo {
  
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

    val list = BoxNow(IndexedSeq(p, q))

    val view = LensRecordView[Person](
      MBoxLens("Name", _.name),
      MBoxLens("Age", _.age)
    )
    
    val ledger = s.transact(implicit txn => ListLedgerBox(list, view))

    val li = s.transact(implicit txn => ListIndices(list))
    
    val i = li.index
    
    val selectedNames = BoxNow.calc(implicit txn => li.selected().map(_.name()).toString)
    
    val selectedNameView = LabelView.apply(selectedNames)
    
    val ledgerView = LedgerView.multiSelectionScroll(ledger, i, true)
    
    val next = BoxNow(0)
    
    val add = new JButton(new AbstractAction("Add") {
      override def actionPerformed(e:ActionEvent) = {
        val person = Person()
        s.transact(implicit txn => {
          person.name() = "New item " + next()
          next() =  next() + 1
          list() = IndexedSeq(person) ++ list()
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
    panel.add(selectedNameView.component)
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