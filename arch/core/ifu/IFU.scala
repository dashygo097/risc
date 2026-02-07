package arch.core.ifu

import arch.configs._
import chisel3._

class Ifu(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_ifu"
}
