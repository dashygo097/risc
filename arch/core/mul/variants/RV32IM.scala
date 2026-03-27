package arch.core.mul

import arch.configs._
import vopts.math.Multiplier

object RV32IMMulUtilities extends RegisteredUtilities[MulUtilities] {
  override def utils: MulUtilities = new MulUtilities {
    override def name: String      = "rv32im"
    override def build: Multiplier = RV32IMulUtilities.utils.build
  }

  override def factory: UtilitiesFactory[MulUtilities] = MulUtilitiesFactory
}
