package arch.core.bpu

import arch.configs._
import chisel3._

class Bpu(implicit p: Parameters) extends Module with BHTConsts {
  override def desiredName: String = s"${p(ISA).name}_bpu"

  val query_pc = IO(Input(Vec(p(IssueWidth), UInt(p(XLen).W))))
  val advance_valid = IO(Input(Bool()))
  val flush         = IO(Input(Bool()))
  val taken    = IO(Output(Vec(p(IssueWidth), Bool())))
  val target   = IO(Output(Vec(p(IssueWidth), UInt(p(XLen).W))))
  val update   = IO(Input(new BpuUpdate))
  val pht_index = IO(Output(Vec(p(IssueWidth), UInt(10.W))))
  val ghr_snapshot = IO(Output(Vec(p(IssueWidth), UInt(10.W))))
  val query_hist = IO(Output(Vec(p(IssueWidth), UInt(10.W))))
  val query_is_branch = IO(Output(Vec(p(IssueWidth), Bool())))
  val debug_gshare_ghr = IO(Output(UInt(10.W)))

  val btb = Module(new Btb)
  val gshare = Module(new GShare)

  btb.query_pc := query_pc
  gshare.query_pc := query_pc
  gshare.query_accept := advance_valid
  gshare.flush := flush

  val branchMask = Wire(Vec(p(IssueWidth), Bool()))
  for (w <- 0 until p(IssueWidth)) {
    val btbHit     = btb.hit(w)
    val dirTaken   = gshare.taken(w)
    val predTaken  = btbHit && dirTaken
    val predTarget = btb.entry_out(w).target

    taken(w)  := predTaken
    target(w) := Mux(predTaken, predTarget, query_pc(w) + 4.U)
    branchMask(w) := btbHit
  }
  gshare.query_is_branch := branchMask

  btb.update := update
  gshare.update := update
  pht_index := gshare.index_out
  ghr_snapshot := gshare.ghr_snapshot_out
  query_hist := gshare.query_hist_out
  query_is_branch := branchMask
  debug_gshare_ghr := gshare.debug_ghr
}
