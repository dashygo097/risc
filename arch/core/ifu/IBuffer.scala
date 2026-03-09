package arch.core.ifu

import arch.configs._
import chisel3._
import chisel3.util._

class IBufferEntry(implicit p: Parameters) extends Bundle {
  val pc    = UInt(p(XLen).W)
  val instr = UInt(p(ILen).W)
}

class IBuffer(implicit p: Parameters) extends Module {
  val enq   = IO(Flipped(Decoupled(new IBufferEntry)))
  val deq   = IO(Decoupled(new IBufferEntry))
  val count = IO(Output(UInt(log2Ceil(p(IBufferSize) + 1).W)))

  val q = Module(new Queue(new IBufferEntry, p(IBufferSize)))

  q.io.enq <> enq
  deq <> q.io.deq
  count := q.io.count
}
