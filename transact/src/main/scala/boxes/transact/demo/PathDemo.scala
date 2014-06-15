package boxes.transact

import boxes.transact._
import boxes.transact.reaction.Path

object PathDemo {

  class Person(val name: Box[String], val friend: Box[Option[Person]]) 
  object Person {
    def apply()(implicit s: Shelf) = s.transact(implicit txn => new Person(Box("Unnamed"), Box(None)))
  }
  
  def main(args: Array[String]): Unit = {
    implicit val s = ShelfDefault()

    val a = Person()
    val b = Person()
    val c = Person()

    
    val aFriend = Path.now(implicit txn => a.friend)

//    val aFriendAndReaction = Path.boxAndReaction(implicit txn => a.friend)
//    val aFriend = aFriendAndReaction._1
//    val reaction = aFriendAndReaction._2
    
    println(">Viewing a's friend's name")
    val v1 = s.view(implicit txn => {
      println(a.name() + "'s friend is " + a.friend().map(_.name()))
    })

    println(">Viewing aFriend's name")
    val v2 = s.view(implicit txn => {
      println("aFriend's name is " + aFriend().map(_.name()))
    })
    
    println("Performing GC")
    1 to 10 foreach {
      _ => System.gc()
    }
    
    //Views are dispatched on another thread, and so we need to give them a while to run
    //in order to see an update for each transaction, otherwise some may be skipped.
    Thread.sleep(100)

    println(">Naming a, b and c")
    s.transact(implicit txn => {
      a.name() = "Alice"
      b.name() = "Bob"
      c.name() = "Charlie"
    })

    Thread.sleep(100)

    println(">a.friend() = Some(b)")
    s.transact(implicit txn => {
      a.friend() = Some(b)
    })
    
    Thread.sleep(100)

    println(">aFriend() = Some(c)")
    s.transact(implicit txn => {
      aFriend() = Some(c)
    })

    Thread.sleep(100)

    println(">aFriend() = Some(a)")
    s.transact(implicit txn => {
      aFriend() = Some(a)
    })

    
  }
}