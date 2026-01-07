package arch.core.csr

import arch.configs._

trait CsrUtilities extends Utilities {
  def cmdWidth: Int
  def addrWidth: Int
}

object CsrUtilitiesFactory extends UtilitiesFactory[CsrUtilities]("CSR")

object CsrInit {
  val rv32iUtils = RV32ICsrUtilities
}
