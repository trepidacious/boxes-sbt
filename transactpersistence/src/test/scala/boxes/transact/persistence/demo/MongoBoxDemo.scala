package boxes.transact.persistence.demo

import boxes.transact.persistence.mongo.MongoMetaNode
import boxes.transact.persistence.mongo.MongoNodeIndex
import boxes.transact.persistence.mongo.MongoNode
import boxes.transact.Txn
import boxes.transact.Box
import boxes.persistence.ClassAliases
import boxes.transact.persistence.mongo.MongoBox
import boxes.transact.ShelfDefault
import boxes.transact.Shelf
import boxes.transact.implicits.Includes._
import boxes.transact.TxnR

object TestNode extends MongoMetaNode {
    override val indices = List(MongoNodeIndex("name"))
    def now(implicit shelf: Shelf) = shelf.transact(implicit txn => new TestNode())
}

class TestNode(implicit txn: Txn) extends MongoNode {
  def meta = TestNode
  val name = Box("bob")
  val index = Box(0)
  def toString(implicit txn: TxnR) = name() + ", " + index()
}

object MongoBoxDemo {

  def main(args: Array[String]) {
    implicit val shelf = ShelfDefault()
    val aliases = {
      val a = new ClassAliases()
      a.alias(classOf[TestNode], "TestNode")
      a
    }
    
    val mb = new MongoBox("MongoBoxDemo", aliases)
    
    val storedBob = mb.findOne[TestNode]("name", "bob")
    read(implicit txn => println(storedBob.map(_.toString)))
    transact(implicit txn => storedBob.foreach(_.name() = "bobob"))
    
    val bob = TestNode.now
    bob.index.now() = 1
    val bobDup = TestNode.now
    bobDup.index.now() = 2
    
    val bill = TestNode.now
    bill.name.now() = "bill"
    bill.index.now() = 42

    shelf.read(implicit txn => {
      mb.keep2(bobDup)
      mb.keep2(bob)
      mb.keep2(bill)
    })
  }
  
}