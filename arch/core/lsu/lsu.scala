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
  val rdata     = IO(Output(UInt(p(XLen).W)))
  val pending   = IO(Output(Bool()))
  val unsigned  = IO(Output(Bool()))
  val mem_read  = IO(Output(Bool()))
  val mem_write = IO(Output(Bool()))

  // Internal Signals
  val pending_reg = RegInit(false.B)
  val byte_offset = addr(1, 0)
  val half_offset = addr(1)

  unsigned  := en && utils.isUnsigned(cmd)
  mem_read  := utils.isMemRead(en, cmd)
  mem_write := utils.isMemWrite(en, cmd)

  val aligned_wdata = MuxCase(
    wdata,
    Seq(
      utils.isByte(cmd) -> (wdata << (byte_offset << 3)),
      utils.isHalf(cmd) -> (wdata << (half_offset << 4)),
      utils.isWord(cmd) -> wdata
    )
  )

  val shifted_rdata = mem.resp.bits.data >> (byte_offset << 3)
  val loaded_data   = MuxCase(
    shifted_rdata,
    Seq(
      utils.isByte(cmd) -> Cat(
        Fill(24, Mux(unsigned, 0.U(1.W), shifted_rdata(7))),
        shifted_rdata(7, 0)
      ),
      utils.isHalf(cmd) -> Cat(
        Fill(16, Mux(unsigned, 0.U(1.W), shifted_rdata(15))),
        shifted_rdata(15, 0)
      ),
      utils.isWord(cmd) -> shifted_rdata
    )
  )

  // Memory request
  mem.req.valid     := (mem_read || mem_write) && !pending
  mem.req.bits.op   := Mux(mem_write, MemoryOp.WRITE, MemoryOp.READ)
  mem.req.bits.addr := addr
  mem.req.bits.data := aligned_wdata

  mem.resp.ready := true.B

  // Pending
  when(mem.req.fire) {
    pending_reg := true.B
  }
  when(mem.resp.fire) {
    pending_reg := false.B
  }
  pending := pending_reg

  // Read Data
  rdata := Mux(mem.resp.fire, loaded_data, 0.U)
}
