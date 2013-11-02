package boxes.demo

import com.mongodb.DBObject
import com.mongodb.casbah.Imports.MongoConnection
import com.mongodb.util.JSON

import boxes.persistence.json.JSONIO

object CasbahDemo {

  def main(args: Array[String]) {
    val mongoConn = MongoConnection()
    
    val nanos = mongoConn("nanos")
    
    val boxes = nanos("boxes")
    
    val io = JSONIO()
    
    val p = Person.testPerson
    
    val json = io.write(p)
    
    val obj = JSON.parse(json).asInstanceOf[DBObject]
    
//    boxes.insert(obj)

    boxes.foreach(o => {
      println("Object id: " + o.get("_id") + ", class " + o.get("_id").getClass())
      o.removeField("_id")
      println(io.read(o.toString).asInstanceOf[Person])
    })
  }

}