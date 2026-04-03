package arch.core.mult

import arch.configs._
import vopts.math.Multiplier

object RV32IMMultUtilities extends RegisteredUtilities[MultUtilities] {
  override def utils: MultUtilities = new MultUtilities {
    override def name: String      = "rv32im"
    override def build: Multiplier = RV32IMultUtilities.utils.build
  }

  override def factory: UtilitiesFactory[MultUtilities] = MultUtilitiesFactory
}
