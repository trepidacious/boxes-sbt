package boxes.transact.node

import collection._
import java.lang.reflect.{Method, Modifier}
import boxes.transact.Box

object Node {

  private val classToMethods = new mutable.HashMap[Class[_], Map[String, Method]]

  def accessors(n:AnyRef) = accessorsOfClass(n.getClass)

  def accessorsOfClass(c:Class[_]) = {
    this.synchronized {
      classToMethods.get(c) match {
        case None => {
          val m = accessorMap(c)
          classToMethods.put(c, m)
          m
        }
        case Some(m) => m
      }
    }
  }

  private def accessorMap(c:Class[_]) = {
    //We want methods that take no parameters and return a Box, and are not
    //static, nor private, nor abstract
    val methods = c.getMethods.toList.filter(m =>
                      classOf[Box[_]].isAssignableFrom(m.getReturnType)
                      && m.getParameterTypes.length == 0
                      && !Modifier.isStatic(m.getModifiers)
                      && !Modifier.isPrivate(m.getModifiers)
                      && !Modifier.isAbstract(m.getModifiers)
    )
    //Map from name to accessor method            //
    val map = Map(methods.map(m => m.getName -> m): _*)
    map
  }
}

//Node is a marker trait for objects that contain all their state in Boxes, each of which
//is reached through a read-only accessor method. Must also have a constructor accepting a Txn
//and constructing a default instance.
trait Node {
  
}