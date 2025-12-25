package arch

import configs._

object ISA  extends Field[String]("rv32i")
object XLen extends Field[Int](32)

object RV32I_CPU_App extends App {
  println(s"Building a ${ISA()} SoC!")
}
