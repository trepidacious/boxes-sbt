package boxes.list

import boxes._

class ListIndexReaction[T](list:ListRef[T], i:Var[Option[Int]], loseIndexOnDeletion:Boolean, selectFirstRatherThanNone:Boolean) extends Reaction {

  private var lastProcessedChangeIndex = -1L

  private def updateIndex(optionI:Option[Int], change:ListChange[T]) = optionI match {

    //No selection remains no selection, under all list changes
    case None => None

    //Some selection is updated
    case Some(i) => change match {
      case ReplacementListChange(_,_,_,_) => Some(i)

      case InsertionListChange(_,_,insertion, count) => if (insertion <= i) Some(i+count) else Some(i)

      case RemovalListChange(_,_,removal, count) => {
        //Deletion after selected index, no effect
        if (removal > i) {
          Some(i)

        //Deletion entirely before selected index
        } else if (removal + count <= i) {
          Some(i-count)

        //Deletion including the selected index
        } else {
          if (loseIndexOnDeletion) {
            None
          //Select the next index AFTER the deleted range.
          //Note that this may put the index AFTER the end of the list,
          //if the deletion covered from the selected index to
          //the end of the list. This case is still valid, since our
          //selection will remain after then end of the list during
          //any possible subsequent changes. After changes have been applied,
          //we will then check for this case, and respond by moving the
          //selection to the end of the list, or None if the list is empty.
          //This is the desired behaviour, since it allows us not to lose the
          //index on such deletions, instead selecting the nearest still
          //existing index.
          } else {
            Some(removal)
          }
        }
      }

      //For a complete change, we reset the index
      case CompleteListChange(_,_) => None
    }

  }

  def respond : (()=>Unit) = {
    var newI = i()
    val size = list().size

    for {
      queue <- list.changes
      indexAndChange <- queue
    } {
      //Only process changes newer than the last one we've already processed.
      //Note that we know we will get changes to the list in the order they are made, with
      //increasing indices.
      if (indexAndChange._1 > lastProcessedChangeIndex) {
        lastProcessedChangeIndex = indexAndChange._1
        newI = updateIndex(newI, indexAndChange._2)
      }
    }

    newI match {
      //Override selection of None if requested and possible
      case None => if (selectFirstRatherThanNone && size > 0) newI = Some(0)

      //Check value
      case Some(newIValue) => {
        //If var is set directly to a negative value, just select nothing
        if (newIValue < 0) {
          newI = None

        //Empty list can't have a selection
        } else if (size == 0) {
          newI = None

        //Selection after end of list indicates a deletion from selection to end of list, response
        //is to select end of list - see above in updateIndex for more details
        } else if (newIValue >= size) {
          newI = Some(size-1)
        }
      }
    }

    val r = newI

    //The write will be performed later
    {() => (i() = r)}
  }

  def isView = false
  def react {respond.apply()}

}


object ListIndex {
  def apply[T](listRef:ListRef[T], initialIndex:Option[Int] = Some(0), loseIndexOnDeletion:Boolean = false, selectFirstRatherThanNone:Boolean = true) = {
    val i = Var(initialIndex)
    val r = new ListIndexReaction[T](listRef, i, loseIndexOnDeletion, selectFirstRatherThanNone)
    i.retainReaction(r)
    Box.registerReaction(r)
    i
  }
}

object ListSelection {
  def apply[T](l:ListRef[T], i:Var[Option[Int]]) = Cal(for (index <- i() if (index >= 0 && index < l().size)) yield l(index))
}

object DefaultSelection extends Enumeration {
   type DefaultSelection = Value
   val FirstIndex, AllIndices, NoIndices = Value
}
import DefaultSelection._


class ListIndicesReaction[T](list:ListRef[T], indices:Var[Set[Int]], loseIndexOnDeletion:Boolean, defaultSelection:DefaultSelection) extends Reaction {

  private var lastProcessedChangeIndex = -1L

  private def updateIndices(is:Set[Int], change:ListChange[T]) = {

    change match {

      case ReplacementListChange(_,_,_,_) => is

      case InsertionListChange(_,_,insertion, count) => is.map{
        i => if (insertion <= i) i+count else i
      }

      //filter out any indices in the deleted range, then adjust those after the range to move them back
      case RemovalListChange(_,_,removal, count) => {
        val newSet = is.collect {
          //Before deletion, no effect
          case i if removal > i => i
          //After deletion, move index back
          case i if (removal + count <= i) => i-count
          //Throw stuff in deletion range away
        }
        //If we don't want to lose index on deletion, and we just have, then
        //select the first index after selection (which is now at the first
        //removed index)
        if (newSet.isEmpty && !loseIndexOnDeletion) {
          Set(removal)
        //Otherwise just keep what we have
        } else {
          newSet
        }
      }

      //For a complete change, we lose the selection
      case CompleteListChange(_,_) => Set[Int]()
    }
  }

  def respond : (()=>Unit) = {
    var newIndices = indices()
    val size = list().size

    for {
      queue <- list.changes
      indexAndChange <- queue
    } {
      //Only process changes newer than the last one we've already processed.
      //Note that we know we will get changes to the list in the order they are made, with
      //increasing indices.
      if (indexAndChange._1 > lastProcessedChangeIndex) {
        lastProcessedChangeIndex = indexAndChange._1
        newIndices = updateIndices(newIndices, indexAndChange._2)
      }
    }

    //If we have selections after the end of the list, then
    //what we actually want is to select the last element of the
    //list. This can only happen when we are aiming to keep a
    //selection even with deletions
    if (newIndices. find(i => i >= size) != None) {
      newIndices = newIndices.filter(i => i < size) + (size-1)
    }

    //Empty list has no selection
    if (size == 0) {
      newIndices = Set[Int]()

    //If empty, select if requested
    } else if (newIndices.isEmpty) {
      defaultSelection match {
        case FirstIndex => newIndices = Set(0)
        case AllIndices => {
          newIndices = Range(0, size).toSet
        }
        case NoIndices => {}
      }
    }

    //Filter out invalid selections
    newIndices = newIndices.filter(i => i >= 0 && i < size)

    val r = newIndices

    //The write will be performed later
    {() => (indices() = r)}
  }

  def isView = false
  def react {respond.apply()}
}

object ListIndices {
  def apply[T](listRef:ListRef[T], initialIndices:Set[Int] = Set[Int](), loseIndexOnDeletion:Boolean = false, defaultSelection:DefaultSelection = FirstIndex) = {
    val i = Var(initialIndices)
    val r = new ListIndicesReaction[T](listRef, i, loseIndexOnDeletion, defaultSelection)
    i.retainReaction(r)
    Box.registerReaction(r)
    i
  }
}
