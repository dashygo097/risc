package arch.core.bpu

import arch.configs._
import vopts.mem.cache._
import chisel3._
import chisel3.util._

class BtbEntry(tagWidth: Int)(implicit p: Parameters) extends Bundle {
  val tag    = UInt(tagWidth.W)
  val target = UInt(p(XLen).W)
  val ctr    = UInt(2.W)
}

class BtbQueryInfo(implicit p: Parameters) extends Bundle {
  val pc = UInt(p(XLen).W)
}

class BtbUpdateInfo(implicit p: Parameters) extends Bundle {
  val pc     = UInt(p(XLen).W)
  val target = UInt(p(XLen).W)
  val taken  = Bool()
}

class Btb(numSets: Int, numWays: Int, replPolicy: ReplacementPolicy)(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_btb"

  // IO
  val query  = IO(Flipped(Decoupled(new BtbQueryInfo)))
  val update = IO(Flipped(Decoupled(new BtbUpdateInfo)))

  // Storage
  val tagWidth = log2Ceil(p(XLen) / 4) // Assuming 4-byte aligned instructions
  val btb      = RegInit(VecInit.fill(numSets, numWays)(0.U.asTypeOf(new BtbEntry(tagWidth))))
}
