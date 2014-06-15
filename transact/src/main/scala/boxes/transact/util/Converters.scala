package boxes.transact.util

trait GConverter[G, T] {
  def toOption(g:G):Option[T]
  def toG(t:T):G
}

//GConverter for where G is just T
class TConverter[T] extends GConverter[T, T] {
  override def toOption(g:T):Option[T] = Some(g)
  override def toG(t:T):T = t
}

//GConverter for where G is Option T
class OptionTConverter[T] extends GConverter[Option[T], T] {
  override def toOption(g:Option[T]):Option[T] = g
  override def toG(t:T):Option[T] = Some(t)
}
