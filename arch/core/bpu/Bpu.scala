package arch.core.bpu

import arch.configs._
import chisel3._

class Bpu(implicit p: Parameters) extends Module with BHTConsts {
  override def desiredName: String = s"${p(ISA).name}_bpu"

  val query_pc      = IO(Input(Vec(p(IssueWidth), UInt(p(XLen).W))))
  val advance_valid = IO(Input(Bool()))
  val flush         = IO(Input(Bool()))
  val taken         = IO(Output(Vec(p(IssueWidth), Bool())))
  val target        = IO(Output(Vec(p(IssueWidth), UInt(p(XLen).W))))
  val update        = IO(Input(new BpuUpdate))
  val pht_index     = IO(Output(Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W))))
  val ghr_snapshot  = IO(Output(Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W))))

  val btb    = Module(new Btb)
  val gshare = Module(new GShare)

  btb.query_pc := query_pc
  btb.update   := update

  gshare.query_pc     := query_pc
  gshare.query_accept := advance_valid && !flush
  gshare.flush        := flush
  gshare.update       := update

  val rawTaken           = Wire(Vec(p(IssueWidth), Bool()))
  val killedByOlderTaken = Wire(Vec(p(IssueWidth), Bool()))
  val branchMask         = Wire(Vec(p(IssueWidth), Bool()))

  killedByOlderTaken(0) := false.B

  for (w <- 0 until p(IssueWidth)) {
    rawTaken(w) := btb.hit(w) && gshare.taken(w)

    if (w > 0) {
      killedByOlderTaken(w) := killedByOlderTaken(w - 1) || rawTaken(w - 1)
    }
  }

  for (w <- 0 until p(IssueWidth)) {
    taken(w)      := rawTaken(w) && !killedByOlderTaken(w)
    target(w)     := Mux(taken(w), btb.entry_out(w).target, query_pc(w) + p(PCStep).U)
    branchMask(w) := btb.hit(w) && !killedByOlderTaken(w)
  }

  gshare.query_is_branch := branchMask

  pht_index    := gshare.index_out
  ghr_snapshot := gshare.ghr_snapshot_out
}
