package boxes.persistence

class StringTokenWriter extends TokenWriter {
  
  private val s = new StringBuilder();
  
  def write(t: Token) {
    if (!s.isEmpty) s.append(", ")
    s.append(t.toString())
  }
  
  override def toString = s.toString
  
  override def close() {
    super.close()
    s.clear
  }
}