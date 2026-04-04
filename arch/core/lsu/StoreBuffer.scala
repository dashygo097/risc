package arch.core.lsu

import arch.configs._
import chisel3._

class StoreBufferEntry(implicit p: Parameters) extends Bundle {}

class StoreBuffer(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_store_buffer"
}
