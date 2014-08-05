package boxes.transact.test


import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import boxes.transact._
import boxes.transact.util.BiMultiMap
import boxes.transact.ShelfDefault

class ReactionPolicySpec extends WordSpec {

  def transientFailureTransact(txn: Txn) = {
    implicit val t = txn
    val a = Box(1)
    val b = Box(2)
    val applied = Box(false)
    txn.createReaction{implicit txn => {
      
      //We expect to fail here when applying immediately, since a() != b()
      if (a() != b()) {
        throw new RuntimeException("Reaction saw unequal a and b") 
      } else {
        applied() = true
      }
      
    }}
    
    //We expect to reach here when applying only before commit, since reaction will still be pending
    b() = 1
    
    //Additionally, we shouldn't yet have applied reaction, since it will only be applied AFTER
    //all code in this transaction
    if (applied()) {
      throw new RuntimeException("Reaction was applied before end of transaction")
    }
    
    //Return the applied Box, so that the test can check it ran at all.
    applied
  }
  
  "ReactionPolicy" should {

    "detect transiently conflicting constraints when applying immediately, and fail" in {
      implicit val s = ShelfDefault()
      
      intercept[FailedReactionsException] {
        s.transact(transientFailureTransact, ReactionImmediate)
      }
      
    }

    "ignore transiently conflicting constraints when applying before commit, then apply the constraint" in {
      implicit val s = ShelfDefault()
      
      val applied = s.transact(transientFailureTransact, ReactionBeforeCommit)
      
      //Check that the reaction was actually applied
      assert(applied.now() === true)      
    }
    
    "apply all reactions created with ReactionBeforeCommit policy" in {
      implicit val s = ShelfDefault()
      
      val e1 = s.transact({implicit txn => {
        val a = Box(0)
        val b = Box(0)
        val c = Box(0)
        val d = Box(0)
        val e = Box(0)
        txn.createReaction{implicit txn => b() = a() + 1}
        txn.createReaction{implicit txn => c() = b() + 1}
        txn.createReaction{implicit txn => d() = c() + 1}
        txn.createReaction{implicit txn => e() = d() + 1}
        a() = 10
        e
      }}, ReactionBeforeCommit)
      
      assert(e1.now() === 14)
      
      val e2 = s.transact({implicit txn => {
        val a = Box(0)
        val b = Box(0)
        val c = Box(0)
        val d = Box(0)
        val e = Box(0)
        txn.createReaction{implicit txn => b() = a() + 1}
        txn.createReaction{implicit txn => c() = b() + 1}
        txn.createReaction{implicit txn => d() = c() + 1}
        txn.createReaction{implicit txn => e() = d() + 1}
        e
      }}, ReactionBeforeCommit)
      
      assert(e2.now() === 4)
    }

  }

}