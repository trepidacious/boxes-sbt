package boxes.transact.test


import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import boxes.transact._
import boxes.transact.util.NamingBiMap
import boxes.transact.persistence.IO
import boxes.transact.node.Node

//This won't work with default values


class PersonBuilder(implicit txn: Txn) {

  //This is the fancier way of implementing the builder default method, which allows for use of default values with boxes
  def default() = apply()
  def apply(name: Box[String] = Box("Name"), age: Box[Int] = Box(20), height: Box[Double] = Box(1.75), enrolled: Box[Boolean] = Box(false), friend: Box[Option[Person]] = Box(None), nicknames: Box[List[String]] = Box(List("Per", "Son")))
    = new Person(name, age, height, enrolled, friend, nicknames)

  //This is the most direct way of implementing the default method
  def default2() = new Person(Box("Name"), Box(20), Box(1.75d), Box(false), Box(None), Box(List("Per", "Son")))

}

class Person(val name: Box[String], val age: Box[Int], val height: Box[Double], val enrolled: Box[Boolean], val friend: Box[Option[Person]], val nicknames: Box[List[String]]) extends Node

class IOSpec extends WordSpec {

  "IO" should {

    "duplicate simple data" in {
      implicit val shelf = ShelfDefault()
      shelf.transact(implicit txn => {
        val builder = new PersonBuilder
        val p = builder()
        val q = builder()
        q.name() = "Q"
        p.friend() = Some(q)
        val s = IO.json().write(p)
        println(s)
        
        val p2 = IO.json().read(s)
        val t = IO.json().write(p2)
        println(t)
        assert(s === t)
      })
    }
    
  }

}