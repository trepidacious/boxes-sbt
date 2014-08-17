package boxes.transact.lift.user

import net.liftweb.util.BCrypt

object Pass {
  
  // gensalt's log_rounds parameter determines the complexity
  // the work factor is 2**log_rounds, and the default is 10
  val workFactor = 12
  
  /**
   * Produce a hash from given password, using BCrypt and salt, using workFactor
   */
  def hash(password: String) = BCrypt.hashpw(password, BCrypt.gensalt(workFactor))
  
  /**
   * Check that a candidate password matches a pregenerated hash
   */
  def check(candidate: String, hashed: String) = BCrypt.checkpw(candidate, hashed)
  
}