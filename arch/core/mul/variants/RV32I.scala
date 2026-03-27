package arch.core.mul

import arch.configs._
import chisel3._
import vopts.math.Multiplier

object RV32IMulUtilities extends RegisteredUtilities[MulUtilities] {
  override def utils: MulUtilities = new MulUtilities {
    override def name: String = "rv32i"

    override def build: Multiplier =
      Module(new Multiplier(p(XLen), pipeline_stages = p(MulPipelineStages)))
  }

  override def factory: UtilitiesFactory[MulUtilities] = MulUtilitiesFactory
}
