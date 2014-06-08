package boxes.transact.fx

import java.util.concurrent.Executor
import javafx.application.Platform
import boxes.transact._
import javafx.beans.property.Property
import javafx.beans.value.ChangeListener
import boxes.transact.Box
import javafx.beans.value.ObservableValue
import boxes.transact.Shelf
import javafx.beans.property._
import scala.math.Numeric
import boxes.util.NumericClass

object JavaFXExecutorService extends Executor {
  override def execute(command: Runnable) = Platform.runLater(command)
}

object Fox {
  def view(f: TxnR => Unit)(implicit shelf: Shelf) = {
    shelf.view(f, JavaFXExecutorService, true)
  }
  def bind[T](b: Box[T], f: Property[T])(implicit shelf: Shelf) = new FoxBindingGeneric(b, f): FoxBinding
  
  def bind(b: Box[String], f: StringProperty)(implicit shelf: Shelf) = new FoxBindingGeneric(b, f): FoxBinding
  def bind(b: Box[Boolean], f: BooleanProperty)(implicit shelf: Shelf) = new FoxBindingBoolean(b, f): FoxBinding
  def bind(b: Box[Int], f: IntegerProperty)(implicit shelf: Shelf) = new FoxBindingInteger(b, f): FoxBinding
  def bind(b: Box[Long], f: LongProperty)(implicit shelf: Shelf) = new FoxBindingLong(b, f): FoxBinding
  def bind(b: Box[Float], f: FloatProperty)(implicit shelf: Shelf) = new FoxBindingFloat(b, f): FoxBinding
  def bind(b: Box[Double], f: DoubleProperty)(implicit shelf: Shelf) = new FoxBindingDouble(b, f): FoxBinding
}

trait FoxBinding

/*
 * Note that we use non-weak listeners and references to the Property and Box in each case. The only important
 * weak reference is from the Box to the transact View, so that the Box will not retain the JavaFX view. We don't
 * mind that the JavaFX view retains the Box - this is desirable in a GUI where the only way the data model is
 * retained is by being displayed in a GUI element.
 */

private class FoxBindingGeneric[T, P <: Property[T]](val b: Box[T], val f: P)(implicit shelf: Shelf) extends ChangeListener[T] with FoxBinding {
  //We don't need any synchronisation on ourRevision or setting vars, since 
  //changed() and the Fox.view are always called from the JavaFX thread.
  var ourRevision: Option[Long] = None
  var setting = false

  f.addListener(this)
  
  def changed(observable: ObservableValue[_ <: T], oldValue: T, newValue: T) = if (!setting) {
    val newRevision = shelf.transactToRevision(implicit txn => b() = newValue)._2.index
    ourRevision = Some(newRevision)
  }
  
  val view = Fox.view(implicit txn => {
    //Always read b, to ensure we view it
    val bv = b()
    val moreRecent = ourRevision.map(_ < txn.revision.index).getOrElse(true)

    //Update if we have a more recent revision - i.e. if we have committed no revisions, or this txn's revision is more recent than
    //the most recent we have committed.
    if (moreRecent) {
      setting = true
      if (f.getValue() != b()) f.setValue(bv)
      setting = false
      ourRevision = Some(txn.revision.index)
    }
  })
}

private class FoxBindingBoolean(val b: Box[Boolean], val f: BooleanProperty)(implicit shelf: Shelf) extends ChangeListener[java.lang.Boolean] with FoxBinding {
  //We don't need any synchronisation on ourRevision or setting vars, since 
  //changed() and the Fox.view are always called from the JavaFX thread.
  var ourRevision: Option[Long] = None
  var setting = false

  f.addListener(this)
  
  def changed(observable: ObservableValue[_ <: java.lang.Boolean], oldValue: java.lang.Boolean, newValue: java.lang.Boolean) = if (!setting) {
    val newRevision = shelf.transactToRevision(implicit txn => b() = newValue)._2.index
    ourRevision = Some(newRevision)
  }
  
  val view = Fox.view(implicit txn => {
    //Always read b, to ensure we view it
    val bv = b()
    val moreRecent = ourRevision.map(_ < txn.revision.index).getOrElse(true)

    //Update if we have a more recent revision - i.e. if we have committed no revisions, or this txn's revision is more recent than
    //the most recent we have committed.
    if (moreRecent) {
      setting = true
      if (f.getValue() != b()) f.setValue(bv)
      setting = false
      ourRevision = Some(txn.revision.index)
    }
  })
}

private class FoxBindingInteger(val b: Box[Int], val f: IntegerProperty)(implicit shelf: Shelf) extends ChangeListener[Number] with FoxBinding {
  //We don't need any synchronisation on ourRevision or setting vars, since 
  //changed() and the Fox.view are always called from the JavaFX thread.
  var ourRevision: Option[Long] = None
  var setting = false

  f.addListener(this)

  def changed(observable: ObservableValue[_ <: Number], oldValue: Number, newValue: Number) = if (!setting) {
    val newRevision = shelf.transactToRevision(implicit txn => b() = newValue.intValue())._2.index
    ourRevision = Some(newRevision)
  }
  
  val view = Fox.view(implicit txn => {
    //Always read b, to ensure we view it
    val bv = b()
    val moreRecent = ourRevision.map(_ < txn.revision.index).getOrElse(true)

    //Update if we have a more recent revision - i.e. if we have committed no revisions, or this txn's revision is more recent than
    //the most recent we have committed.
    if (moreRecent) {
      setting = true
      if (f.getValue() != b()) f.setValue(bv)
      setting = false
      ourRevision = Some(txn.revision.index)
    }
  })
}

private class FoxBindingLong(val b: Box[Long], val f: LongProperty)(implicit shelf: Shelf) extends ChangeListener[Number] with FoxBinding {
  //We don't need any synchronisation on ourRevision or setting vars, since 
  //changed() and the Fox.view are always called from the JavaFX thread.
  var ourRevision: Option[Long] = None
  var setting = false

  f.addListener(this)

  def changed(observable: ObservableValue[_ <: Number], oldValue: Number, newValue: Number) = if (!setting) {
    val newRevision = shelf.transactToRevision(implicit txn => b() = newValue.longValue())._2.index
    ourRevision = Some(newRevision)
  }
  
  val view = Fox.view(implicit txn => {
    //Always read b, to ensure we view it
    val bv = b()
    val moreRecent = ourRevision.map(_ < txn.revision.index).getOrElse(true)

    //Update if we have a more recent revision - i.e. if we have committed no revisions, or this txn's revision is more recent than
    //the most recent we have committed.
    if (moreRecent) {
      setting = true
      if (f.getValue() != b()) f.setValue(bv)
      setting = false
      ourRevision = Some(txn.revision.index)
    }
  })
}

private class FoxBindingFloat(val b: Box[Float], val f: FloatProperty)(implicit shelf: Shelf) extends ChangeListener[Number] with FoxBinding {
  //We don't need any synchronisation on ourRevision or setting vars, since 
  //changed() and the Fox.view are always called from the JavaFX thread.
  var ourRevision: Option[Long] = None
  var setting = false

  f.addListener(this)

  def changed(observable: ObservableValue[_ <: Number], oldValue: Number, newValue: Number) = if (!setting) {
    val newRevision = shelf.transactToRevision(implicit txn => b() = newValue.floatValue())._2.index
    ourRevision = Some(newRevision)
  }
  
  val view = Fox.view(implicit txn => {
    //Always read b, to ensure we view it
    val bv = b()
    val moreRecent = ourRevision.map(_ < txn.revision.index).getOrElse(true)

    //Update if we have a more recent revision - i.e. if we have committed no revisions, or this txn's revision is more recent than
    //the most recent we have committed.
    if (moreRecent) {
      setting = true
      if (f.getValue() != b()) f.setValue(bv)
      setting = false
      ourRevision = Some(txn.revision.index)
    }
  })
}

private class FoxBindingDouble(val b: Box[Double], val f: DoubleProperty)(implicit shelf: Shelf) extends ChangeListener[Number] with FoxBinding {
  //We don't need any synchronisation on ourRevision or setting vars, since 
  //changed() and the Fox.view are always called from the JavaFX thread.
  var ourRevision: Option[Long] = None
  var setting = false

  f.addListener(this)

  def changed(observable: ObservableValue[_ <: Number], oldValue: Number, newValue: Number) = if (!setting) {
    val newRevision = shelf.transactToRevision(implicit txn => b() = newValue.doubleValue())._2.index
    ourRevision = Some(newRevision)
  }
  
  val view = Fox.view(implicit txn => {
    //Always read b, to ensure we view it
    val bv = b()
    val moreRecent = ourRevision.map(_ < txn.revision.index).getOrElse(true)

    //Update if we have a more recent revision - i.e. if we have committed no revisions, or this txn's revision is more recent than
    //the most recent we have committed.
    if (moreRecent) {
      setting = true
      if (f.getValue() != b()) f.setValue(bv)
      setting = false
      ourRevision = Some(txn.revision.index)
    }
  })
}
