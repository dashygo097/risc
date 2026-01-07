package arch.core.csr

import arch.configs._
import chisel3._

class CsrFile(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_csrfile"

  val utils = CsrUtilitiesFactory.getOrThrow(p(ISA))
}
