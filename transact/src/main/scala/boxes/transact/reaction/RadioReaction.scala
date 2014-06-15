package boxes.transact.reaction

import boxes.transact._

object RadioReaction {
  def now(options: Box[Boolean]*)(implicit shelf: Shelf) = shelf.transact(implicit txn => apply(options: _*))
  def apply(options: Box[Boolean]*)(implicit txn: Txn) = {
    val r = txn.createReaction(implicit rt => {
      val activeOptions = options.filter(o => o())
      
      //If more than one option is selected, find the best option to leave selected
      if (activeOptions.size > 1) {
        //Use the first selected option that has changed, otherwise just the first selected option
        val changedSelectedOptions = activeOptions.toSet.intersect(rt.changedSources)
        val selected = changedSelectedOptions.headOption match {
          case Some(o) => o
          case _ => activeOptions.head
        }
        activeOptions.foreach{ao => if (ao ne selected) ao() = false}
      }
    })
    options.foreach(o => o.retainReaction(r))
    r
  }
}
