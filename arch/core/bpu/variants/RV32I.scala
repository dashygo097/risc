package arch.core.bpu

import arch.configs._

object RV32IBpuUtilities extends RegisteredUtilities[BpuUtilities] {
  override def utils: BpuUtilities = new BpuUtilities {
    override def name: String = "rv32i"
  }

  override def factory: UtilitiesFactory[BpuUtilities] = BpuUtilitiesFactory
}
