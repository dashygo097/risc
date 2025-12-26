package arch.core.regfile

import arch.configs._
import chisel3._

class Regfile(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_regfile"

}
