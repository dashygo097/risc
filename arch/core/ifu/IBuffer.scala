package arch.core.ifu

import arch.configs._
import chisel3._
import chisel3.util._

class IBufferEntry(implicit p: Parameters) extends Bundle {
  val pc              = UInt(p(XLen).W)
  val instr           = UInt(p(ILen).W)
  val bpu_pred_taken  = Bool()
  val bpu_pred_target = UInt(p(XLen).W)
}

class IBuffer(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_ibuffer"

  val enq   = IO(Flipped(Decoupled(new IBufferEntry)))
  val deq   = IO(Decoupled(new IBufferEntry))
  val empty = IO(Output(Bool()))
  val full  = IO(Output(Bool()))

  val q = Module(new Queue(new IBufferEntry, p(IBufferSize)))

  q.io.enq <> enq
  deq <> q.io.deq
  empty := q.io.count === 0.U
  full  := q.io.count === p(IBufferSize).U
}
