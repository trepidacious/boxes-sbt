package boxes.transact.util

import scala.collection.immutable.HashMap

/**
 * A BiMap implementation modelling naming, where
 * keys are named by values, and each key may have more than one
 * name, but only one canonical name, and each name can only refer to
 * one key:
 * Any key maps to None or Some(value) (i.e. 0 or 1 values). This is the canonical name of the key, if such a name exists.
 * Any value maps back to None or Some(key) (i.e. 0 or 1 keys). This is the key named by this value, if such a key exists.
 * There may be multiple values all of which map to the same key - this is permitted. These are alternate names for the key, only one name is canonical.
 * NO value can be mapped to from multiple keys. This implies that no name is reused for multiple keys.
 * 
 * This can be used, for example, to assign short names to classes, in a way that ensures we can recover
 * the original class from a name, and that if necessary we can use a new canonical name for a class while
 * still storing any previous name(s).
 */
trait NamingBiMap[K, V] {
  def valueFor(key: K): Option[V];
  def keyFor(value: V): Option[K];
  def updated(key: K, value: V): NamingBiMap[K, V];
}

private class NamingBiMapDefault[K, V](val forward: Map[K, V], val backward: Map[V, K]) extends NamingBiMap[K, V] {
  def valueFor(key: K): Option[V] = forward.get(key)
  def keyFor(value: V): Option[K] = backward.get(value)
  
  def updated(key: K, value: V): NamingBiMap[K, V] = {
    
    //First check whether we are already using this value for an existing key
    val existingKey = backward.get(value)
    existingKey.foreach(k => throw new RuntimeException(k + " is already mapped to " + value))
    
    val newForward = forward.updated(key, value)
    val newBackward = backward.updated(value, key)
    
    //We now have both plain maps updated, make a new BiMultiMap
    new NamingBiMapDefault(newForward, newBackward)
  }
  override def toString() = "F " + forward + ", B " + backward
}

object NamingBiMap {
  def empty[K, V]: NamingBiMap[K, V] = new NamingBiMapDefault[K, V](HashMap.empty, HashMap.empty)
}

