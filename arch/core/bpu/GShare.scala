package arch.core.bpu

import arch.configs._
import chisel3._
import chisel3.util.Cat 

class GShare(implicit p: Parameters) extends Module with BHTConsts {
  override def desiredName: String = s"${p(ISA).name}_gshare"

  val query_pc         = IO(Input(Vec(p(IssueWidth), UInt(p(XLen).W))))
  val query_is_branch  = IO(Input(Vec(p(IssueWidth), Bool())))
  val query_accept     = IO(Input(Bool()))
  val flush            = IO(Input(Bool()))
  val taken            = IO(Output(Vec(p(IssueWidth), Bool())))
  val update           = IO(Input(new BpuUpdate))
  val index_out        = IO(Output(Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W))))
  val ghr_snapshot_out = IO(Output(Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W))))

  val phtEntries = 1 << p(GShareGhrWidth)

  require(p(GShareGhrWidth) >= 2, "GShareGhrWidth must be at least 2")

  val commitGhr = RegInit(0.U(p(GShareGhrWidth).W))
  val specGhr   = RegInit(0.U(p(GShareGhrWidth).W))

  // Initialize all counters to weakly-taken to reduce cold-start penalty.
  val pht = RegInit(VecInit(Seq.fill(phtEntries)(BHT_WT.value.U(SZ_BHT.W))))

  val snt = BHT_SNT.value.U(SZ_BHT.W)
  val st  = BHT_ST.value.U(SZ_BHT.W)

  def getIndex(pc: UInt, hist: UInt): UInt = {
    val pcLow  = pc(p(GShareGhrWidth) + 1, p(PCAlign))
    // Fold higher PC bits to reduce table aliasing for nearby hot loops.
    val pcHigh = pc((2 * p(GShareGhrWidth)) + 1, p(GShareGhrWidth) + 2)
    pcLow ^ pcHigh ^ hist
  }

  def shiftHist(hist: UInt, taken: Bool): UInt =
    Cat(hist(p(GShareGhrWidth) - 2, 0), taken)

  val queryGhr = Wire(Vec(p(IssueWidth) + 1, UInt(p(GShareGhrWidth).W)))
  queryGhr(0) := specGhr

  for (w <- 0 until p(IssueWidth)) {
    val index    = getIndex(query_pc(w), queryGhr(w))
    val counter  = pht(index)
    val dirTaken = counter(1)

    taken(w)            := dirTaken
    index_out(w)        := index
    ghr_snapshot_out(w) := queryGhr(w)
    queryGhr(w + 1)     := Mux(query_is_branch(w), shiftHist(queryGhr(w), dirTaken), queryGhr(w))
  }

  when(update.valid) {
    val uIndex = update.pht_index

    // Update PHT with saturating 2-bit counter.
    val oldCnt = pht(uIndex)
    val newCnt = Mux(update.taken, Mux(oldCnt === st, st, oldCnt + 1.U), Mux(oldCnt === snt, snt, oldCnt - 1.U))
    pht(uIndex) := newCnt
    commitGhr   := shiftHist(update.ghr_snapshot, update.taken)
  }

  when(query_accept) {
    specGhr := queryGhr(p(IssueWidth))
  }

  when(flush) {
    specGhr := commitGhr
  }

  when(update.valid && update.mispredict) {
    specGhr := shiftHist(update.ghr_snapshot, update.taken)
  }
}
