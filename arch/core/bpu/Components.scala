package arch.core.bpu

import arch.configs._

trait BpuUtilities extends Utilities {}

object BpuUtilitiesFactory extends UtilitiesFactory[BpuUtilities]("BPU")

object BpuInit {
  val rv32iUtils = RV32IBpuUtilities
}
