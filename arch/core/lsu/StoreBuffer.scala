package arch.core.lsu

import arch.configs._
import chisel3._

class StoreBuffer(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_store_buffer"
}
