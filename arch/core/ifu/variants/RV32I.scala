package arch.core.ifu

import arch.configs._

object RV32IIfuUtilities extends RegisteredUtilities[IfuUtilities] {
  override def utils: IfuUtilities = new IfuUtilities {
    override def name: String = "rv32i"
  }

  override def factory: UtilitiesFactory[IfuUtilities] = IfuUtilitiesFactory
}
