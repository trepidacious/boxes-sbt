package boxes.lift.box

import boxes.persistence.ClassAliases
import boxes.persistence.mongo.MongoBox
import com.mongodb.ServerAddress
import net.liftweb.util.Props

object Data {  
  val aliases = new ClassAliases
  //FIXME require setup in Boot to define database, server etc.
  val mb = new MongoBox("boxes", aliases)
}

