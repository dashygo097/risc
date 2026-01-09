package arch.system.crossbar

import arch.configs._
import chisel3._

class BusCrossbar(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(BusType)}_crossbar"

  val utils = BusCrossbarUtilitiesFactory.getOrThrow(p(BusType))
}
