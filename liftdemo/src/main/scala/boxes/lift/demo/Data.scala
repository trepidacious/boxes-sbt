package boxes.lift.demo

import boxes.NumericVarImplicits._
import boxes._
import boxes.list.ListVar
import boxes.persistence.ClassAliases
import boxes.persistence.mongo.MongoBox
import java.security.MessageDigest
import net.liftweb.util.StringHelpers
import net.liftweb.util.SecurityHelpers

trait Pointed {
  def points: Ref[Int]
}

trait Named {
  def name: Ref[String]
}

object Frame {
  val maxSystemsPerFrame = Var(4) from 0
  val maxSystemsPerType = Var(2) from 0
  val pointsPerSystem = Var(1) from 0
  val pointsPerFrame = Var(5) from 0
  
  def apply(
        name: String = "New Frame", 
        model: String = "New Model", 
        handToHand: Int = 0, 
        direct: Int = 1, 
        artillery: Int = 0, 
        comms: Int = 1, 
        movement: Int = 1, 
        defensive:Int = 1
      ) = {
    val f = new Frame
    f.name() = name
    f.model() = model
    f.handToHand() = handToHand    
    f.direct() = direct
    f.artillery() = artillery
    f.comms() = comms
    f.movement() = movement
    f.defensive() = defensive
    f
  }
}

class Frame extends Node with Pointed with Named{
  val name = Var("New Frame")
  val model = Var("New Model")
  
  val handToHand = Var(0) from 0 to Frame.maxSystemsPerType
  val direct = Var(0) from 0 to Frame.maxSystemsPerType
  val artillery = Var(0) from 0 to Frame.maxSystemsPerType
  val comms = Var(0) from 0 to Frame.maxSystemsPerType
  val movement = Var(0) from 0 to Frame.maxSystemsPerType
  val defensive = Var(0) from 0 to Frame.maxSystemsPerType
  
  val systems = Cal{handToHand() + direct() + artillery() + comms() + movement() + defensive()}
  
  val valid = Cal{systems() <= Frame.maxSystemsPerFrame()}

  val points = Cal(systems() * Frame.pointsPerSystem() + Frame.pointsPerFrame())
}

object Squad {
  def apply(name: String = "New Squad", frames: List[Frame] = List(
        Frame("Frame 1", "Soldier"),
        Frame("Frame 2", "Soldier"),
        Frame("Frame 3", "Brawler",   2, 0, 0, 0, 0, 2),
        Frame("Frame 4", "Artillery", 0, 1, 0, 1, 1, 1)
      )) = {
    val s = new Squad
    s.name() = name
    s.frames.append(frames:_*)
    s
  }
}

class Squad extends Node with Pointed with Named {
  val name = Var("New Squad")
  val frames = ListVar[Frame]()
  val points = Cal{frames().map(_.points()).sum}
}


