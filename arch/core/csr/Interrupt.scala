package arch.core.csr

import chisel3._

class CoreInterruptIO extends Bundle {
  val timer_irq = Input(Bool())
  val soft_irq  = Input(Bool())
  val ext_irq   = Input(Bool())
}
