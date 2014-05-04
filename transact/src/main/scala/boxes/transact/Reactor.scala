package boxes.transact

/**
 * The interface available to Reactions as they run, which provides both the
 * transaction the Reaction is running in, and additional data that is only
 * useful to Reactions, and only exists while a Reactor is processing reactions
 * in one "cycle".
 */
trait ReactorTxn extends Txn {
  
  /**
   * Get the set of Boxes that are sources of the Reaction, and have changed
   * during this Reactor's execution
   */
  def changedSources: Set[Box[_]]
}

/**
 * ReactorForTxn and TxnForReactor provide one possible means of communication
 * between a Reactor and a Transaction, for example when using the ReactorDefault
 * implementation, but is not part of public API.
 */
trait ReactorForTxn {
  def afterSet[T](box: Box[T], t: T): Unit  
  def afterGet[T](box: Box[T]): Unit
  def registerReaction(r: ReactionDefault): Unit
}

/**
 * ReactorForTxn and TxnForReactor provide one possible means of communication
 * between a Reactor and a Transaction, for example when using the ReactorDefault
 * implementation, but is not part of public API.
 */
trait TxnForReactor extends Txn {
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