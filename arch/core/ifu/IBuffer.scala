package arch.core.ifu

import arch.configs._
import chisel3._
import chisel3.util.{ Decoupled, PopCount, log2Ceil, isPow2 }

class IBufferEntry(implicit p: Parameters) extends Bundle {
  val pc              = UInt(p(XLen).W)
  val instr           = UInt(p(ILen).W)
  val bpu_pred_taken  = Bool()
  val bpu_pred_target = UInt(p(XLen).W)
  val bpu_pht_index   = UInt(10.W)
  val bpu_ghr_snapshot = UInt(10.W)
}

class IBuffer(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_ibuffer"

  require(isPow2(p(IBufferSize)), "IBufferSize must be a power of 2")

  val io = IO(new Bundle {
    val enq_valid = Input(Vec(p(IssueWidth), Bool()))
    val enq_bits  = Input(Vec(p(IssueWidth), new IBufferEntry))
    val enq_ready = Output(Bool())

    val deq   = Vec(p(IssueWidth), Decoupled(new IBufferEntry))
    val empty = Output(Bool())
    val full  = Output(Bool())
    val flush = Input(Bool())
  })

  val buffer = Reg(Vec(p(IBufferSize), new IBufferEntry))
  val count  = RegInit(0.U(log2Ceil(p(IBufferSize) + 1).W))
  val head   = RegInit(0.U(log2Ceil(p(IBufferSize)).W))
  val tail   = RegInit(0.U(log2Ceil(p(IBufferSize)).W))

  val enq_valids = io.enq_valid.map(_.asUInt)
  val enq_count  = PopCount(io.enq_valid)

  val enq_offsets = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(IBufferSize)).W)))
  enq_offsets(0)   := 0.U
  for (w <- 1 until p(IssueWidth))
    enq_offsets(w) := (enq_offsets(w - 1) + enq_valids(w - 1))(log2Ceil(p(IBufferSize)) - 1, 0)

  io.enq_ready := (p(IBufferSize).U - count) >= p(IssueWidth).U
  val do_enq = io.enq_ready && io.enq_valid.reduce(_ || _)

  val mask = (p(IBufferSize) - 1).U

  when(do_enq) {
    for (w <- 0 until p(IssueWidth))
      when(io.enq_valid(w)) {
        val idx = ((tail + enq_offsets(w)) & mask)(log2Ceil(p(IBufferSize)) - 1, 0)
        buffer(idx) := io.enq_bits(w)
      }
  }

  val deq_fires = io.deq.map(_.fire)
  val deq_count = PopCount(deq_fires)

  for (w <- 0 until p(IssueWidth)) {
    io.deq(w).valid := count > w.U
    val idx = if (w == 0) head else ((head + w.U) & mask)(log2Ceil(p(IBufferSize)) - 1, 0)
    io.deq(w).bits := buffer(idx)
  }

  head := ((head + deq_count) & mask)(log2Ceil(p(IBufferSize)) - 1, 0)

  val actual_enq_count = Mux(do_enq, enq_count, 0.U)
  tail  := ((tail + actual_enq_count) & mask)(log2Ceil(p(IBufferSize)) - 1, 0)
  count := count + actual_enq_count - deq_count

  when(io.flush) {
    count := 0.U
    head  := 0.U
    tail  := 0.U
  }

  io.empty := count === 0.U
  io.full  := count === p(IBufferSize).U
}
