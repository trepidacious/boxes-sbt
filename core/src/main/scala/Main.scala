import boxes._

object Hi {
  def simpleCalc() = {

    println()
    println("simpleCalc")

    val a = Var(2)
    val b = Cal(a() + 3)
    val c = Cal(a() + b())

    println("b = " + b() + ", c = " + c())

    a() = 4

    println("b = " + b() + ", c = " + c())

  }

  def main(args: Array[String]) {
    simpleCalc
  }
}