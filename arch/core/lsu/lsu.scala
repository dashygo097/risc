package arch.core.lsu

import arch.configs._
import mem.cache._
import chisel3._

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
  val ready     = IO(Output(Bool()))
  val unsigned  = IO(Output(Bool()))
  val mem_read  = IO(Output(Bool()))
  val mem_write = IO(Output(Bool()))
  val strb      = IO(Output(UInt((p(XLen) / 8).W)))

  // Internal state
  val read_data = RegInit(0.U(p(XLen).W))
  val pending   = RegInit(false.B)

  // Control signals
  unsigned := en && utils.isUnsigned(cmd)
  val is_read  = en && utils.isRead(cmd)
  val is_write = en && utils.isWrite(cmd)
  mem_read  := is_read
  mem_write := is_write
  strb      := Mux(en, utils.strb(cmd), 0.U)
  ready     := !pending

  // Memory request generation
  mem.req.valid     := (is_read || is_write) && !pending
  mem.req.bits.op   := Mux(is_write, MemoryOp.WRITE, MemoryOp.READ)
  mem.req.bits.addr := addr
  mem.req.bits.data := wdata

  mem.resp.ready := true.B

  // State machine
  when(mem.req.fire) {
    pending := true.B
  }
  when(mem.resp.fire) {
    read_data := mem.resp.bits.data
    pending   := false.B
  }

  // Read data output
  rdata := read_data
  when(is_read && mem.resp.fire) {
    rdata := mem.resp.bits.data
  }
}
