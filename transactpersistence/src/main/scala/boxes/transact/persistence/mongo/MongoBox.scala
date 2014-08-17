package boxes.transact.persistence.mongo

import com.mongodb.casbah.Imports._
import scala.collection.mutable.WeakHashMap
import com.mongodb.DBObject
import com.mongodb.util.JSON
import boxes.util.WeakKeysBIDIMap
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.MongoException
import boxes.persistence.ClassAliases
import boxes.transact.node.Node
import boxes.transact.persistence.JSONIO
import boxes.transact.util.RWLock
import boxes.transact.Shelf
import boxes.transact.ReactionPolicy
import boxes.transact.ReactionBeforeCommit
import scala.util.Try
import boxes.transact.View
import java.util.concurrent.atomic.AtomicReference
import boxes.transact.util.Lock

case class MongoNodeIndex(key: String, unique: Boolean = true, ascending: Boolean = true)

//Meta data about a node, can for example be shared between all instances of a particular MongoNode instance
//Optional, but allows for indexing when implemented
trait MongoMetaNode {
  def indices: List[MongoNodeIndex] = List()
}

trait MongoNode extends Node {
  def meta: MongoMetaNode

  //TODO nicer way to do this?
  private val retainedView = new AtomicReference(null: View)
  def retainView(view: View) = retainedView.set(view)
}

class MongoBox(dbName: String, aliases: ClassAliases)(implicit shelf: Shelf) {

  private val mongoConn = MongoConnection()
  private val db = mongoConn(dbName)
  private val io = JSONIO(aliases)
  
  //TODO might be better as soft references, to reduce unneeded db access?
  private val idMap = new WeakKeysBIDIMap[MongoNode, ObjectId]()
  private val idMapLock = Lock()
  
  def id(t: MongoNode) = idMapLock(idMap.toValue(t))

  private def toMongoNode[T <: MongoNode](alias: String, dbo: MongoDBObject) = idMapLock {
    for {
      id <- dbo._id
    } yield {
      idMap.toKey(id).map(_.asInstanceOf[T]).getOrElse {
        //We are reading, so use a transaction where we react only before we commit
        val fromMongo = shelf.transact(implicit txn => io.readDBO(dbo).asInstanceOf[T], ReactionBeforeCommit)
        track(alias, fromMongo, id)
        fromMongo
      }
    }    
  }

  def findById[T <: MongoNode](id: String)(implicit man: Manifest[T]): Option[T] = Try(new ObjectId(id)).toOption.flatMap(oid => findById(oid)(man))
  
  def findById[T <: MongoNode](id: ObjectId)(implicit man: Manifest[T]): Option[T] = findOne(MongoDBObject("_id" -> id))(man) 

  def findOne[T <: MongoNode](key: String, value: Any)(implicit man: Manifest[T]): Option[T] = findOne(MongoDBObject(key -> value))(man)
    
  def findOne[T <: MongoNode](query: MongoDBObject)(implicit man: Manifest[T]): Option[T] = {
    val alias = aliases.forClass(man.runtimeClass)
    for {
      dbo <- db(alias).findOne(query)
      n <- toMongoNode[T](alias, dbo)
    } yield n
  }
  
  def find[T <: MongoNode](query: MongoDBObject)(implicit man: Manifest[T]): Iterator[T] = {
    val alias = aliases.forClass(man.runtimeClass)
    val cursor = db(alias).find(query)
    cursor.flatMap(dbo => toMongoNode[T](alias, dbo))
  }
  
  private def useMongoNode(alias: String, mn: MongoNode) {
    mn.meta.indices.foreach(
      i => db(alias).ensureIndex(
        MongoDBObject(i.key -> (if (i.ascending) 1 else -1)),  i.key,  i.unique))
  }
  
  private def track(alias:String, t: MongoNode, id: ObjectId) = idMapLock {
    
    //First make sure any indices are in place, so we respect them from the View we will create
    useMongoNode(alias, t)

    //Set up a View that writes any changes to mongo
    val query = MongoDBObject("_id" -> id)
    t.retainView(
      //TODO look at best scheduler for writing, we can make writing very parallel for
      //independent documents as represented by MongoNode
      shelf.view(implicit txn => {
        val dbo = io.writeDBO(t).asInstanceOf[MongoDBObject]
        db(alias).update(query, dbo)
      })
    )
    
    idMap.put(t, id)
  }
  
  //Register a MongoNode to be kept in mongodb. Returns the ObjectId used. If the
  //MongoNode was already kept, nothing is done, but the ObjectId is still returned.
  def keep(t: MongoNode) = idMapLock {
    //Get the existing id for the node, or else add the
    //node to mongo and return the new id
    idMap.toValue(t).getOrElse{        
      val alias = aliases.forClass(t.getClass())
      
      //First make sure any indices are in place, so we respect them when writing the
      //new record to DB
      useMongoNode(alias, t)
      
      //Make a DB object with new id, and insert it to mongo
      val id = new ObjectId()
      val dbo = shelf.transact(implicit txn => io.writeDBO(t).asInstanceOf[MongoDBObject])
      dbo.put("_id", id)
      
      //TODO detect errors, e.g. index problems (duplicate ObjectId, or conflict with an index from MongoNode)
      db(alias).insert(dbo)
      
      val leOrNull = db(alias).lastError.getException()
      if (leOrNull != null) throw leOrNull 
        
      //Set up View and add to map
      track(alias, t, id)
      id        
    }
  }
  
  def forget(t: MongoNode) = idMapLock {
    //Get the existing id for the node
    idMap.toValue(t).foreach(id => {
      val alias = aliases.forClass(t.getClass())
      //Remove from mongo and our map
      db(alias).remove(MongoDBObject("_id" -> id))
      idMap.removeKey(t)
    })
  }
  
}