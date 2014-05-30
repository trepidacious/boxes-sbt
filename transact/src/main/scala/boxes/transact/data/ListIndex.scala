package boxes.transact.data

import boxes.transact.Shelf
import boxes.transact.Box
import boxes.transact.Txn

trait ListIndex[T] {
  val selected: Box[Option[T]]
  val index: Box[Option[Int]]
}

private class ListIndexDefault[T](val selected: Box[Option[T]], val index: Box[Option[Int]]) extends ListIndex[T]

object ListIndex {
  
  def apply[T](list: Box[_ <: Seq[T]])(implicit txn: Txn) = {
    val selected: Box[Option[T]] = Box(None)
    val index: Box[Option[Int]] = Box(None)
    
    val r = txn.createReaction(implicit rt => {
      val l = list()
      val s = selected()
      val i = index()

      val cs = rt.changedSources

      def useIndex() = {
        i match {
          case None => {
//            println("No index, clearing selection")
            selected() = None
          }
          case Some(i) if i < 0 => {
//            println("index < 0, using 0")
            index() = Some(0)
            selected() = Some(l(0))          
          }
          case Some(i) if i >= l.size => {
//            println("index >= list size, using list size - 1")
            index() = Some(l.size-1)
            selected() = Some(l(l.size-1))          
          }
          case Some(i) => {
//            println("index is in list, using it")
            index() = Some(i)
            selected() = Some(l(i))          
          }
        }
      }

      def clear() = {
        index() = None
        selected() = None        
      }
      
      def consistent() = (i, s) match {
        case (None, None) => true
        case (Some(i), Some(s)) if i >= 0 && i < l.size => s == l(i)
        case _ => false
      }
      
      //TODO support default selection other than None
      def default() = {
        index() = None
        selected() = None
      }
      
      //If we are already consistent, nothing to do
      if (consistent) {
//        println("Already consistent")
      //If list is empty, no selection
      } else if (l.isEmpty) {
//        println("Empty list, clearing")
        clear()
        
      //If just the index has changed, it is authoritative
      } else if (cs == Set(index)) {
//        println("Just index changed, using index " + i)
        useIndex()
        
      //Otherwise try to use the selection
      } else {
//        println("Trying selection")

        val newIndex = s.map(s => l.indexOf(s)).getOrElse(-1)
        
        //Selection is still in list, update index
        if (newIndex > -1) {
//          println("Selection is in list, using")
          index() = Some(newIndex)
          
        //Selection is not in list, if just list has changed, use
        //index to look up new selection
        } else if (cs == Set(list)) {
//          println("Selection not in list, using index " + i)
          useIndex()
          
        //Otherwise just use default
        } else {
//          println("Defaulting")
          default()
        }
      }
      
    })
    
    selected.retainReaction(r)
    index.retainReaction(r)
    
    new ListIndexDefault(selected, index): ListIndex[T]
  }
}