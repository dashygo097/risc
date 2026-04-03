package arch.core.mult

import arch.configs._
import chisel3._
import vopts.math.Multiplier

object RV32IMultUtilities extends RegisteredUtilities[MultUtilities] {
  override def utils: MultUtilities = new MultUtilities {
    override def name: String = "rv32i"

    override def build: Multiplier =
      Module(new Multiplier(p(XLen), pipeline_stages = p(MultPipelineStages)))
  }

  override def factory: UtilitiesFactory[MultUtilities] = MultUtilitiesFactory
}
