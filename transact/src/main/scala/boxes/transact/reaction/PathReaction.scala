package boxes.transact.reaction

import boxes.transact._
import boxes.transact.util.TConverter
import boxes.transact.util.OptionTConverter
import boxes.transact.util.GConverter

//TODO Refactor this to an object that just returns the txn=>Unit directly
class PathReaction[T, G](v:Box[G], path : Txn => Option[Box[T]], defaultValue:G, c:GConverter[G, T]) {
  def react(implicit txn: ReactorTxn) {
    path(txn) match {
      //If the path is disconnected, revert to default
      case None => v() = defaultValue

      //Otherwise do the mirroring
      case Some(e) => {
        //e() is type T, and v() is type G, so to compare we need to lift e() to type G
        val eContents = c.toG(e())
        val vContents = v()

        //If contents differ, resolve
        if (eContents != vContents) {
          //Only propagate from v to e if only v has changed - otherwise, propagate from e to v.
          //This means that only changes directly to v that have NOT altered the path OR the endpoint
          //will be applied back to the endpoint.
          if (txn.changedSources != Set(v)) {
            v() = eContents
          } else {
            //Take vContents of type G up to a definite Option, then we
            //can check whether it is None, and otherwise extract its
            //value as type T to copy to e
            c.toOption(vContents) match {
              case None => v() = eContents
              case Some(vValue) => e() = vValue
            }
          }
        }
      }
    }
  }
}

object Path {
  def now[T](path : Txn => Box[T])(implicit s: Shelf) = s.transact(implicit txn => boxAndReaction(path)._1)
  def apply[T](path : Txn => Box[T])(implicit txn: Txn) = boxAndReaction(path)._1
  
  def boxAndReaction[T](path : Txn => Box[T])(implicit txn: Txn) = {    
    val e = path(txn)
    val eVal = e()
    val v = Box(eVal)
    //Here we have both v and endpoint as parametric type T, so no need for
    //any converstion - use a TConverter. We do raise the path to an Option, but
    //since it always works we just use Some(path). Default value doesn't matter since
    //it is never used. Apologies for the null, but it really is NEVER used. Could
    //use eVal instead, but this potentially creates a memory leak.
    val pr = new PathReaction[T, T](v, (txn: Txn) => Some(path(txn)), null.asInstanceOf[T], new TConverter[T])
    val r = txn.createReaction(pr.react(_))
    v.retainReaction(r)
    (v, r)
  }
}


/**
 * Creates paths that go TO a Var[Option[T]], AND may also go VIA an option.
 * If the path goes via an option, it may yield None, in which case the
 * produced Var will contain None.
 * If the path yields Some(Var[Option[T]]) then the produced Var will
 * contain the value of the Var[Option[T]], which may be Some(tValue) or
 * None.
 *
 * Note this is (slightly confusingly) equivalent to PathWithDefault(path, None),
 * it just makes explicit that T from pathWithDefault is now Option[T], and the
 * defaultValue is None. This is probably the most common way of using a path
 * that leads to a Var[Option[T]].
 */
object PathToOption {
  def now[T](path : Txn => Option[Box[Option[T]]])(implicit s: Shelf) = s.transact(implicit txn => boxAndReaction(path)._1)
  def apply[T](path : Txn => Option[Box[Option[T]]])(implicit txn: Txn) = boxAndReaction(path)._1

  def boxAndReaction[T](path : Txn => Option[Box[Option[T]]])(implicit txn: Txn) = {
    val v:Box[Option[T]] = Box(None)
    //Not going to pretend this isn't confusing... here we use a TConverter
    //because we are producing v with parametric type Option[T], and using
    //a path to an endpoint with parametric type Option[T]. There is hence no
    //mismatch in levels of Option, and we don't need to convert anything. So
    //the "T" in "TConverter" is actually "Option[T]" in this case.
    val pr = new PathReaction[Option[T], Option[T]](v, path, None, new TConverter[Option[T]])
    val r = txn.createReaction(pr.react(_))
    v.retainReaction(r)
    (v, r)
  }
}

/**
 * Creates paths that go VIA an option, but lead to a Var that contains a
 * nonoptional value. So this covers the case where following the path
 * may yield either None, OR a Var[T] for some non-optional type T.
 * To allow for this, the returned Var is a Var[Option[T]], which contains
 * Some(tValue) when the path yields a Var[T], and None when the
 * path yields None.
 */
object PathViaOption {
  def now[T](path : Txn => Option[Box[T]])(implicit s: Shelf) = s.transact(implicit txn => boxAndReaction(path)._1)
  def apply[T](path : Txn => Option[Box[T]])(implicit txn: Txn) = boxAndReaction(path)._1
  
  def boxAndReaction[T](path: Txn => Option[Box[T]])(implicit txn: Txn) = {
    val v:Box[Option[T]] = Box(None)
    //Here we have to raise values in our endpoint Var (of parametric type T)
    //up to Option[T], so we use an OptionTConverter.
    val pr = new PathReaction[T, Option[T]](v, path, None, new OptionTConverter[T])
    val r = txn.createReaction(pr.react(_))
    v.retainReaction(r)
    (v, r)
  }
}
