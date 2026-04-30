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

  btb.query_pc        := query_pc
  btb.update          := update
  gshare.query_pc     := query_pc
  gshare.query_accept := advance_valid
  gshare.flush        := flush
  gshare.update       := update

  val raw_taken             = Wire(Vec(p(IssueWidth), Bool()))
  val killed_by_older_taken = Wire(Vec(p(IssueWidth), Bool()))
  val branch_mask           = Wire(Vec(p(IssueWidth), Bool()))

  killed_by_older_taken(0) := false.B

  for (w <- 0 until p(IssueWidth)) {
    raw_taken(w) := btb.hit(w) && gshare.taken(w)
    if (w > 0) {
      killed_by_older_taken(w) := killed_by_older_taken(w - 1) || raw_taken(w - 1)
    }
  }

  for (w <- 0 until p(IssueWidth)) {
    taken(w)       := raw_taken(w) && !killed_by_older_taken(w)
    target(w)      := Mux(taken(w), btb.entry_out(w).target, query_pc(w) + p(PCStep).U)
    branch_mask(w) := btb.hit(w) && !killed_by_older_taken(w)
  }

  gshare.query_is_branch := branch_mask
  pht_index              := gshare.index_out
  ghr_snapshot           := gshare.ghr_snapshot_out
}
