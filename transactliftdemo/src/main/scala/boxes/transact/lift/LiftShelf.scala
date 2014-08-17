package boxes.transact.lift

import boxes.transact.Shelf
import boxes.transact.ShelfDefault
import boxes.persistence.ClassAliases
import boxes.transact.persistence.mongo.MongoBox

object LiftShelf {
  implicit val shelf: Shelf = ShelfDefault()
  
  val aliases = new ClassAliases
  
  //FIXME require setup in Boot to define database, server etc.
  val mb = new MongoBox("boxes", aliases)
}