package arch.core.bpu

import arch.configs._
import chisel3._

class Bpu(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_bpu"

  val utils = BpuUtilitiesFactory.getOrThrow(p(ISA))

  val query_pc = IO(Input(UInt(p(XLen).W)))
  val taken    = IO(Output(Bool()))
  val target   = IO(Output(UInt(p(XLen).W)))
  val update   = IO(Input(new BpuUpdate))

  val btb = Module(new Btb)

  btb.query_pc := query_pc

  val btbHit     = btb.hit
  val ctr        = btb.entry_out.ctr
  val predTaken  = btbHit && ctr(1)
  val predTarget = btb.entry_out.target

  taken  := predTaken
  target := Mux(predTaken, predTarget, query_pc + 4.U)

  btb.update := update
}
