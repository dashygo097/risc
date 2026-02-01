package arch.core.ifu

import arch.configs._

trait IfuUtilities extends Utilities {}

object IfuUtilitiesFactory extends UtilitiesFactory[IfuUtilities]("IFU")

object IfuInit {
  val rv32iUtils = RV32IIfuUtilities
}
