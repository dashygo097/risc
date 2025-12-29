package arch.core.lsu

import arch.configs._
import mem.cache._
import chisel3._
import chisel3.util._

class Lsu(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_lsu"

  val utils = LsuUtilitiesFactory.getOrThrow(p(ISA))

  // Control inputs
  val en  = IO(Input(Bool()))
  val cmd = IO(Input(UInt(utils.cmdWidth.W)))

  // Data inputs
  val addr  = IO(Input(UInt(p(XLen).W)))
  val wdata = IO(Input(UInt(p(XLen).W)))

  // Memory interface
  val mem = IO(new UnifiedMemoryIO(p(XLen), p(XLen), 1, 1))

  // Outputs
  val pending   = IO(Output(Bool()))
  val unsigned  = IO(Output(Bool()))
  val mem_read  = IO(Output(Bool()))
  val mem_write = IO(Output(Bool()))
  val strb      = IO(Output(UInt((p(XLen) / 8).W)))

  // Internal Signals
  val pending_reg = RegInit(false.B)

  unsigned  := en && utils.isUnsigned(cmd)
  mem_read  := utils.isMemRead(en, cmd)
  mem_write := utils.isMemWrite(en, cmd)
  strb      := Mux(en, utils.strb(cmd), 0.U)

  // Memory request generation
  mem.req.valid     := (mem_read || mem_write) && !pending
  mem.req.bits.op   := Mux(mem_write, MemoryOp.WRITE, MemoryOp.READ)
  mem.req.bits.addr := addr
  mem.req.bits.data := MuxLookup(strb, 0.U)(
    Seq(
      "b0001".U -> (wdata & 0xff.U),
      "b0011".U -> (wdata & 0xffff.U),
      "b1111".U -> wdata
    )
  )

  mem.resp.ready := true.B

  // Pending
  when(mem.req.fire) {
    pending_reg := true.B
  }
  when(mem.resp.fire) {
    pending_reg := false.B
  }
  pending := pending_reg

}
