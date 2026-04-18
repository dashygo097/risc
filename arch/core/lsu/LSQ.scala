package arch.core.lsu

import arch.configs._
import chisel3._

class LSQ(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_lsq"
}
