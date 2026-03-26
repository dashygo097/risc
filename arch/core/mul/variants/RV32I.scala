package arch.core.mul

import arch.configs._
import chisel3._
import vopts.math.Multiplier

trait RV32IMulConsts extends MulConsts

object RV32IMulUtilities extends RegisteredUtilities[MulUtilities] with RV32IMulConsts {
  override def utils: MulUtilities = new MulUtilities {
    override def name: String = "rv32i"

    override def build(dw: Int): Multiplier = {
      Module(new Multiplier(dw, pipeline_stages = p(MulPipelineStages)))
    }
  }

  override def factory: UtilitiesFactory[MulUtilities] = MulUtilitiesFactory
}