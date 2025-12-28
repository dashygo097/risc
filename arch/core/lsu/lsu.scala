package arch.core.lsu

import arch.configs._
import chisel3._

class Lsu(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_lsu"

  val utils = LsuUtilitiesFactory.getOrThrow(p(ISA))
}
