package arch.core.lsu

import arch.configs._
import chisel3._

class LoadUnit(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_load_unit"
}
