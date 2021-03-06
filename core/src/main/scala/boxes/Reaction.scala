package boxes

import scala.collection.mutable.Set

object Reaction {

  class SingleTargetReaction[T](v:VarBox[T, _], result: =>T) extends Reaction {
    def isView = false
    def react {v()=result}
  }

  def apply[T](v:VarBox[T, _], result: =>T) = {
    val r = new SingleTargetReaction(v, result)
    Box.registerReaction(r)
    v.retainReaction(r)
    r
  }
  
  class DefaultReaction(result: =>Unit) extends Reaction {
    def isView = false
    def react {result}
  }

  def apply(result: =>Unit) = {
    val r = new DefaultReaction(result)
    Box.registerReaction(r)
    r
  }

  def apply(b: Box[_,_], result: =>Unit) = {
    val r = new DefaultReaction(result)
    Box.registerReaction(r)
    b.retainReaction(r)
    r
  }

}

object OptionalReaction {
  
  class OptionalSingleTargetReaction[T](v:VarBox[T, _], result: =>Option[T]) extends Reaction {
    def isView = false
    def react {result.foreach{v() = _}}
  }

  def apply[T](v:VarBox[T, _], result: =>Option[T]) = {
    val r = new OptionalSingleTargetReaction(v, result)
    Box.registerReaction(r)
    v.retainReaction(r)
    r
  }

}

/**
 * One part of the boxes system, the other is Box.
 *
 * NEVER EVER change Reaction equals/hashCode methods - Reactions
 * MUST only be equal when they are the same (identical) Reaction.
 * This is because sets and maps are used to track them.
 */
trait Reaction {

  def react()

  def isView : Boolean

  private[boxes] val sources = Set[Box[_,_]]()
  private[boxes] val targets = Set[Box[_,_]]()

}




