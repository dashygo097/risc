package arch

import configs._

object ISA  extends Field[String]("rv32i")
object ILen extends Field[Int](32)
object XLen extends Field[Int](32)

object CPU extends App {
  println(s"Building a $ISA CPU")
}
