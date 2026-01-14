package arch.core.rename

import arch.configs._

object RV32IRenameUtilities extends RegisteredUtilities[RenameUtilities] {
  override def utils: RenameUtilities = new RenameUtilities {
    override def name: String = "rv32i"

  }

  override def factory: UtilitiesFactory[RenameUtilities] = RenameUtilitiesFactory
}
