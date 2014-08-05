package boxes.transact.test


import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import boxes.transact._
import boxes.transact.util.BiMultiMap

class BiMultiMapSpec extends WordSpec {

  "BiMultiMap" should {

    "update" in {
      val m = BiMultiMap.empty[String, Int]
      
      val mu = m.updated("a", Set(1, 2, 3, 4))

      //Create mapping for "a" and check forwards and backwards
      assert (mu.valuesFor("a") === Set(1, 2, 3, 4))
      Set(1, 2, 3, 4).foreach(v => assert(mu.keysFor(v) === Set("a")))

      //Check unmapped keys and values are empty
      assert (mu.valuesFor("b") === Set())
      assert (mu.keysFor(5) === Set())
      
      //Change mapping for "a", check forwards and backwards, and check old backwards mappings are removed
      val mu2 = mu.updated("a", Set(3, 4, 5, 6))
      assert (mu2.valuesFor("a") === Set(3, 4, 5, 6))
      Set(3, 4, 5, 6).foreach(v => assert(mu2.keysFor(v) === Set("a")))
      Set(1, 2).foreach(v => assert(mu2.keysFor(v) === Set()))
      
    }

    "support multiple keys per value" in {
      val m = BiMultiMap.empty[String, Int].updated("a", Set(1, 2, 3, 4)).updated("b", Set(3, 4, 5, 6))
      assert (m.valuesFor("a") === Set(1, 2, 3, 4))
      assert (m.valuesFor("b") === Set(3, 4, 5, 6))
      
      Set(1, 2).foreach(v => assert(m.keysFor(v) === Set("a")))
      Set(3, 4).foreach(v => assert(m.keysFor(v) === Set("a", "b")))
      Set(5, 6).foreach(v => assert(m.keysFor(v) === Set("b")))
    }
        
    "remove key" in {
      val m = BiMultiMap.empty[String, Int].updated("a", Set(1, 2, 3, 4)).updated("b", Set(3, 4, 5, 6))
      
      val mr = m.removedKey("a")
      
      //Check forward and backwards mappings removed
      assert (mr.valuesFor("a") === Set())
      assert (mr.valuesFor("b") === Set(3, 4, 5, 6))
      Set(1, 2).foreach(v => assert(mr.keysFor(v) === Set()))      
      Set(3, 4, 5, 6).foreach(v => assert(mr.keysFor(v) === Set("b")))      
    }
    
    "remove value" in {
      val m = BiMultiMap.empty[String, Int].updated("a", Set(1, 2, 3, 4)).updated("b", Set(3, 4, 5, 6))

      val mr = m.removedValue(3)
      
      //Check forward and backwards mappings removed
      assert (mr.valuesFor("a") === Set(1, 2, 4))
      assert (mr.valuesFor("b") === Set(4, 5, 6))
      
      Set(1, 2).foreach(v => assert(mr.keysFor(v) === Set("a")))
      Set(3).foreach(v => assert(mr.keysFor(v) === Set()))
      Set(4).foreach(v => assert(mr.keysFor(v) === Set("a", "b")))
      Set(5, 6).foreach(v => assert(mr.keysFor(v) === Set("b")))    
    }
    
    "remove keys" in {
      val m = BiMultiMap.empty[String, Int].updated("a", Set(1, 2, 3, 4)).updated("b", Set(3, 4, 5, 6)).updated("c", Set(5, 6, 7, 8))
      
      val mr = m.removedKeys(Set("a", "b"))
      
      //Check forward and backwards mappings removed
      assert (mr.valuesFor("a") === Set())
      assert (mr.valuesFor("b") === Set())
      assert (mr.valuesFor("c") === Set(5, 6, 7, 8))
      Set(1, 2, 3, 4).foreach(v => assert(mr.keysFor(v) === Set()))      
      Set(5, 6, 7, 8).foreach(v => assert(mr.keysFor(v) === Set("c")))      
    }
    
    "remove values" in {
      val m = BiMultiMap.empty[String, Int].updated("a", Set(1, 2, 3, 4)).updated("b", Set(3, 4, 5, 6))

      val mr = m.removedValues(Set(1, 3))
      
      //Check forward and backwards mappings removed
      assert (mr.valuesFor("a") === Set(2, 4))
      assert (mr.valuesFor("b") === Set(4, 5, 6))
      
      Set(1).foreach(v => assert(mr.keysFor(v) === Set()))
      Set(2).foreach(v => assert(mr.keysFor(v) === Set("a")))
      Set(3).foreach(v => assert(mr.keysFor(v) === Set()))
      Set(4).foreach(v => assert(mr.keysFor(v) === Set("a", "b")))
      Set(5, 6).foreach(v => assert(mr.keysFor(v) === Set("b")))    
    }
    
  }

}