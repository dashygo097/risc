package arch.core.lsu

import arch.configs._
import mem.cache._
import chisel3._
import chisel3.util._

class Lsu(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_lsu"
  val utils                        = LsuUtilitiesFactory.getOrThrow(p(ISA))

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

  // Internal state
  val pending_reg = RegInit(false.B)
  val rdata_reg   = RegInit(0.U(p(XLen).W))

  val byte_offset = addr(1, 0)

  rdata     := 0.U(p(XLen).W)
  unsigned  := en && utils.isUnsigned(cmd)
  mem_read  := utils.isMemRead(en, cmd)
  mem_write := utils.isMemWrite(en, cmd)

  // Write data alignment
  val aligned_wdata = MuxCase(
    wdata,
    Seq(
      utils.isByte(cmd) -> Cat(Fill(24, 0.U), wdata(7, 0)),
      utils.isHalf(cmd) -> Cat(Fill(16, 0.U), wdata(15, 0)),
      utils.isWord(cmd) -> wdata
    )
  )

  // Read data processing
  val shifted_rdata = mem.resp.bits.data >> (byte_offset << 3)
  val loaded_data   = MuxCase(
    shifted_rdata,
    Seq(
      utils.isByte(cmd) -> Cat(
        Fill(24, !utils.isUnsigned(cmd) && shifted_rdata(7)),
        shifted_rdata(7, 0)
      ),
      utils.isHalf(cmd) -> Cat(
        Fill(16, !utils.isUnsigned(cmd) && shifted_rdata(15)),
        shifted_rdata(15, 0)
      ),
      utils.isWord(cmd) -> shifted_rdata
    )
  )

  // Memory request
  mem.req.valid     := !pending_reg && (mem_read || mem_write)
  mem.req.bits.op   := Mux(mem_write, MemoryOp.WRITE, MemoryOp.READ)
  mem.req.bits.addr := addr
  mem.req.bits.data := aligned_wdata
  mem.resp.ready    := true.B

  // State machine
  when(mem.req.fire) {
    pending_reg := true.B
  }

  when(mem.resp.fire) {
    pending_reg := false.B
    rdata_reg   := loaded_data
  }

  pending := pending_reg
  rdata   := rdata_reg
}
