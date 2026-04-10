package arch.core.ifu

import arch.configs._
import chisel3._
import chisel3.util.{ Decoupled, PopCount, log2Ceil }

class IBufferEntry(implicit p: Parameters) extends Bundle {
  val pc              = UInt(p(XLen).W)
  val instr           = UInt(p(ILen).W)
  val bpu_pred_taken  = Bool()
  val bpu_pred_target = UInt(p(XLen).W)
}

class IBuffer(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_ibuffer"

  val enq   = IO(Flipped(Decoupled(new IBufferEntry)))
  val deq   = IO(Vec(p(IssueWidth), Decoupled(new IBufferEntry)))
  val empty = IO(Output(Bool()))
  val full  = IO(Output(Bool()))
  val flush = IO(Input(Bool()))

  val buffer = Reg(Vec(p(IBufferSize), new IBufferEntry))
  val count  = RegInit(0.U(log2Ceil(p(IBufferSize) + 1).W))
  val head   = RegInit(0.U(log2Ceil(p(IBufferSize)).W))
  val tail   = RegInit(0.U(log2Ceil(p(IBufferSize)).W))

  enq.ready := count < p(IBufferSize).U
  val do_enq = enq.fire
  when(do_enq)(buffer(tail) := enq.bits)

  val deq_fires = deq.map(_.fire)
  val deq_count = PopCount(deq_fires)

  for (w <- 0 until p(IssueWidth)) {
    deq(w).valid := count > w.U
    val idx = if (w == 0) head else ((head + w.U) % p(IBufferSize).U)(log2Ceil(p(IBufferSize)) - 1, 0)
    deq(w).bits := buffer(idx)
  }

  head  := ((head + deq_count)     % p(IBufferSize).U)(log2Ceil(p(IBufferSize)) - 1, 0)
  tail  := ((tail + do_enq.asUInt) % p(IBufferSize).U)(log2Ceil(p(IBufferSize)) - 1, 0)
  count := count + do_enq.asUInt - deq_count

  when(flush) {
    count := 0.U
    head  := 0.U
    tail  := 0.U
  }

  empty := count === 0.U
  full  := count === p(IBufferSize).U
}
