package boxes.lift.box

import boxes.persistence.ClassAliases
import boxes.persistence.mongo.MongoBox

object Data {  
  val aliases = new ClassAliases
  val mb = new MongoBox("boxes", aliases)
}

