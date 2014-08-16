package boxes.transact.test


import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import boxes.transact._
import boxes.transact.util.NamingBiMap

class NamingBiMapSpec extends WordSpec {

  "NamingBiMap" should {

    "update" in {
      val m = NamingBiMap.empty[String, String]
      
      val mu = m.updated("Person", "Dude")

      //Check forwards and backwards
      assert (mu.valueFor("Person") === Some("Dude"))
      assert (mu.keyFor("Dude") === Some("Person"))

      //Check some other key isn't mapped
      assert (mu.valueFor("Dude") === None)
      assert (mu.keyFor("Person") === None)
      assert (mu.valueFor("a") === None)
      assert (mu.keyFor("a") === None)

    }

    "allow multiple value for one key, last one canonical" in {
      val m = NamingBiMap.empty[String, String]
      
      val mu = m.updated("Person", "Dude")

      //Check forwards and backwards
      assert (mu.valueFor("Person") === Some("Dude"))
      assert (mu.keyFor("Dude") === Some("Person"))

      //With a new value, the most recent value is canonical, but both
      //values still map back to key
      val mu2 = mu.updated("Person", "Guy")
      assert (mu2.valueFor("Person") === Some("Guy"))
      assert (mu2.keyFor("Dude") === Some("Person"))
      assert (mu2.keyFor("Guy") === Some("Person"))

      //And again
      val mu3 = mu2.updated("Person", "HU-MAN")
      assert (mu3.valueFor("Person") === Some("HU-MAN"))
      assert (mu3.keyFor("Dude") === Some("Person"))
      assert (mu3.keyFor("Guy") === Some("Person"))
      assert (mu3.keyFor("HU-MAN") === Some("Person"))
    }
    
    "reject multiple keys for one value" in {
      val m = NamingBiMap.empty[String, String]
      
      val mu = m.updated("Person", "Dude")

      //Check forwards and backwards
      assert (mu.valueFor("Person") === Some("Dude"))
      assert (mu.keyFor("Dude") === Some("Person"))

      //With a new value, the most recent value is canonical, but both
      //values still map back to key
      intercept[RuntimeException] {mu.updated("Hedgehog", "Dude")}
    }
    
  }

}