package arch.core.bru

import arch.configs._
import chisel3._

class Bru(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_bru"

  val utils = BruUtilitiesFactory.getOrThrow(p(ISA))
}
