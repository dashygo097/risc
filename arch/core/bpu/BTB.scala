package arch.core.bpu

import arch.configs._
import vopts.mem.cache._
import chisel3._
import chisel3.util._

class BtbEntry(tagWidth: Int)(implicit p: Parameters) extends Bundle with BHTConsts {
  val valid  = Bool()
  val tag    = UInt(tagWidth.W)
  val target = UInt(p(XLen).W)
  val ctrl   = UInt(SZ_BHT.W)
}

object BtbEntry extends BHTConsts {
  def default(tagWidth: Int)(implicit p: Parameters): BtbEntry = {
    val e = Wire(new BtbEntry(tagWidth))
    e.valid  := false.B
    e.tag    := 0.U
    e.target := 0.U
    e.ctrl   := BHT_WT.value.U(SZ_BHT.W)
    e
  }
}

class BpuUpdate(implicit p: Parameters) extends Bundle {
  val valid  = Bool()
  val pc     = UInt(p(XLen).W)
  val target = UInt(p(XLen).W)
  val taken  = Bool()
}

class Btb(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_btb"

  private val iAlignWidth = log2Ceil(p(IAlign))
  private val indexWidth  = log2Ceil(p(BTBSets))
  private val tagWidth    = p(XLen) - indexWidth - iAlignWidth

  val query_pc  = IO(Input(UInt(p(XLen).W)))
  val hit       = IO(Output(Bool()))
  val entry_out = IO(Output(new BtbEntry(tagWidth)))
  val update    = IO(Input(new BpuUpdate))

  val entries = RegInit(
    VecInit(
      Seq.fill(p(BTBSets))(
        VecInit(
          Seq.fill(p(BTBWays))(
            0.U.asTypeOf(new BtbEntry(tagWidth))
          )
        )
      )
    )
  )

  val replStates = Seq.fill(p(BTBSets))(new PseudoLRUState(p(BTBWays)))

  def getIndex(pc: UInt): UInt = pc(indexWidth + 1, iAlignWidth)
  def getTag(pc: UInt): UInt   = pc(p(XLen) - 1, indexWidth + iAlignWidth)

  val qIndex = getIndex(query_pc)
  val qTag   = getTag(query_pc)
  val qSet   = entries(qIndex)

  val hitBits: Seq[Bool] = (0 until p(BTBWays)).map { w =>
    qSet(w).valid && (qSet(w).tag === qTag)
  }
  val anyHit             = hitBits.reduce(_ || _)
  val hitWay             = PriorityEncoder(VecInit(hitBits))

  hit       := anyHit
  entry_out := Mux(anyHit, qSet(hitWay), 0.U.asTypeOf(new BtbEntry(tagWidth)))

  when(update.valid) {
    val uIndex = getIndex(update.pc)
    val uTag   = getTag(update.pc)
    val uSet   = entries(uIndex)

    val uHitBits: Seq[Bool] = (0 until p(BTBWays)).map { w =>
      uSet(w).valid && (uSet(w).tag === uTag)
    }
    val uAnyHit             = uHitBits.reduce(_ || _)
    val uHitWay             = PriorityEncoder(VecInit(uHitBits))

    val victimWay = Wire(UInt(log2Ceil(p(BTBWays)).W))
    victimWay := 0.U
    for (s <- 0 until p(BTBSets))
      when(s.U === uIndex)(victimWay := replStates(s).getVictim())

    val writeWay = Mux(uAnyHit, uHitWay, victimWay)

    val newEntry = Wire(new BtbEntry(tagWidth))
    newEntry.valid  := true.B
    newEntry.tag    := uTag
    newEntry.target := update.target

    val oldCtrl = uSet(writeWay).ctrl
    newEntry.ctrl := Mux(update.taken, Mux(oldCtrl === 3.U, 3.U, oldCtrl + 1.U), Mux(oldCtrl === 0.U, 0.U, oldCtrl - 1.U))

    entries(uIndex)(writeWay) := newEntry

    for (s <- 0 until p(BTBSets))
      when(s.U === uIndex)(replStates(s).update(writeWay, uAnyHit))
  }
}
