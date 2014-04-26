package boxes.transact.util

import scala.collection.immutable.HashSet
import scala.collection.immutable.HashMap

class BiMultiMap[K, V](forward: Map[K, Set[V]], backward: Map[V, Set[K]]) {
  def valuesFor(key: K): Set[V] = forward.get(key).getOrElse(HashSet.empty)
  def keysFor(value: V): Set[K] = backward.get(value).getOrElse(HashSet.empty)
  
  def updated(key: K, values: Set[V]): BiMultiMap[K, V] = {
    val newForward = forward.updated(key, values)
    val oldValues = valuesFor(key)
    
    val addedValues = values -- oldValues     //Values that have been added to the mapping for key
    val removedValues = oldValues -- values   //Values that have been removed from the mapping for key

    //For each value that is no longer mapped from key, remove the backward mapping from that value to key 
    val removedBackward = removedValues.foldLeft(backward){case (b, removed) => b.updated(removed, b.get(removed).getOrElse(HashSet.empty) - key)}
    
    //For each value that is newly mapped from key, add the backward mapping from that value to key (starting from removedBackward)
    val addedBackward = addedValues.foldLeft(removedBackward){case (b, added) => b.updated(added, b.get(added).getOrElse(HashSet.empty) + key)}
    
    //We now have both plain maps updated, make a new BiMultiMap
    new BiMultiMap(newForward, addedBackward)
  }
  
  def removedKey(key: K): BiMultiMap[K, V] = {
    val values = valuesFor(key)
    val newForward = forward - key
    val newBackward = values.foldLeft(backward){case (b, value) => b.updated(value, b.get(value).getOrElse(HashSet.empty) - key)}
    new BiMultiMap(newForward, newBackward)
  }
  
  def removedKeys(keys: Set[K]): BiMultiMap[K, V] = keys.foldLeft(this){case (bmm, key) => bmm.removedKey(key)}
  
  def removedValue(value: V): BiMultiMap[K, V] = {
    val keys = keysFor(value)
    val newBackward = backward - value
    val newForward = keys.foldLeft(forward){case (f, key) => f.updated(key, f.get(key).getOrElse(HashSet.empty) - value)}
    new BiMultiMap(newForward, newBackward)
  }

  def removedValues(values: Set[V]): BiMultiMap[K, V] = values.foldLeft(this){case (bmm, value) => bmm.removedValue(value)}
  
  override def toString() = "F " + forward + ", B " + backward
}

object BiMultiMap {
  def empty[K, V] = new BiMultiMap[K, V](HashMap.empty, HashMap.empty)
}

