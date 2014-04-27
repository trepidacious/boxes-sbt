package boxes.transact

trait Reactor {
  def afterSet[T](box: Box[T], t: T): Unit  
  def afterGet[T](box: Box[T]): Unit
  def registerReaction(r: ReactionDefault): Unit
}

trait ReactorTxn extends Txn {
  def reactionFinished()
  def clearReactionSourcesAndTargets(rid: Long)
  
  def targetsOfReaction(rid: Long): Set[Long]
  def sourcesOfReaction(rid: Long): Set[Long]
  
  def reactionsTargettingBox(bid: Long): Set[Long]
  def reactionsSourcingBox(bid: Long): Set[Long]
  
  def react(rid: Long)
  
  def addTargetForReaction(rid: Long, bid: Long)
  def addSourceForReaction(rid: Long, bid: Long)
}