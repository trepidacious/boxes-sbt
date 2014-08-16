package boxes.transact.test


import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import boxes.transact._
import boxes.transact.util.NamingBiMap
import boxes.transact.persistence.IO
import boxes.transact.node.Node

//This won't work with default values
class Person(implicit txn: Txn) extends Node {
  val name = Box("Name")
  val age = Box(20)
  val height = Box(1.75d)
  val enrolled = Box(false)
  val friend: Box[Option[Person]] = Box(None)
  val nicknames = Box(List("Per", "Son"))
}

class IOSpec extends WordSpec {

  "IO" should {

    "write json" in {
      implicit val shelf = ShelfDefault()
      shelf.transact(implicit txn => {
        val p = new Person
        val q = new Person
        q.name() = "Q"
        p.friend() = Some(q)
        val s = IO.json().write(p)
        println(s)
        
        val p2 = IO.json().read(s)
        println(IO.json().write(p2))
      })
    }
    
  }

}