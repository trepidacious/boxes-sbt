package boxes.transact.data

import boxes.transact.Shelf
import boxes.transact.Box
import boxes.transact.Txn
import scala.collection.immutable._

trait ListIndices[T] {
  val selected: Box[Set[T]]
  val index: Box[Set[Int]]
}

private class ListIndicesDefault[T](val selected: Box[Set[T]], val index: Box[Set[Int]]) extends ListIndices[T]

object ListIndices {
  
  def apply[T](list: Box[_ <: Seq[T]], selectAllByDefault: Boolean = true)(implicit txn: Txn) = {
    val selected: Box[Set[T]] = Box(Set())
    val index: Box[Set[Int]] = Box(Set())
    
    val r = txn.createReaction(implicit rt => {
      val l = list()
      val s = selected()
      val i = index()

      val cs = rt.changedSources

      def useFirstIndex() = {
        //Use first selected index, and try to preserve it
        i.reduceLeftOption{(a, b) => Math.min(a, b)} match {
          case None => {
//            println("No index, clearing selection")
            default()
          }
          case Some(i) if i < 0 => {
//            println("index < 0, using 0")
            index() = Set(0)
            selected() = Set(l(0))          
          }
          case Some(i) if i >= l.size => {
//            println("index >= list size, using list size - 1")
            index() = Set(l.size-1)
            selected() = Set(l(l.size-1))
          }
          case Some(i) => {
//            println("index is in list, using it")
            index() = Set(i)
            selected() = Set(l(i))          
          }
        }
      }
      
      def useIndices() = {
        //Filter indices to be in list
        val newI = i.filter(i => i >= 0 && i < l.size)
        //If empty, use default
        if (newI.isEmpty) {
          default()
          
        //Otherwise use indices to update selection
        } else {
          selected() = newI.map(i => l(i))
        }
      }

      def clear() = {
        index() = Set()
        selected() = Set()        
      }
      
      def consistent() = (i, s, l) match {
        case (i, s, Nil) if i.isEmpty && s.isEmpty => true                  //Must select nothing in empty list
        case (i, s, _) if i.isEmpty && s.isEmpty => !selectAllByDefault     //In non-empty list, selecting nothing is ok iff we are not selecting all by default
        case (i, s, l) => {                                                 //If we have a selection and a list, then check all indices are in list, and selection matches indices
          val iInList = i.filter(i => i >= 0 && i < l.size)
          iInList.size == i.size && iInList.map(i => l(i)) == s
        }
      }
      
      def default() = {
        if (!selectAllByDefault || l.isEmpty) {
          index() = Set()
          selected() = Set()
        } else {
          index() = Range(0, l.size).toSet
          selected() = index().map(i => l(i))
        }
      }
      
      //If we are already consistent, nothing to do
      if (consistent) {
        println("Already consistent")
        
      //If list is empty, no selection
      } else if (l.isEmpty) {
        println("Empty list, clearing")
        clear()
        
      //If just the index has changed, it is authoritative
      } else if (cs == Set(index)) {
        println("Just index changed, using indices " + i)
        useIndices()
        
      //Otherwise try to use the selection
      } else {
//        println("Trying selection")

        //Find indices of selection in list
        val newIndices = s.map(l.indexOf(_)).filter(_ > -1)
        
        //Some of selection is still in list, update index
        if (!newIndices.isEmpty) {
//          println("Selection is still at least partially in list, using")
          index() = newIndices
          
        //Selection is completely missing from list, if just list has changed, use
        //first index to look up new selection
        } else if (cs == Set(list)) {
//          println("Selection not in list, using first index from " + i)
          useFirstIndex()
          
        //Otherwise just use default
        } else {
//          println("Defaulting")
          default()
        }
      }
      
    })
    
    selected.retainReaction(r)
    index.retainReaction(r)
    
    new ListIndicesDefault(selected, index): ListIndices[T]
  }
}