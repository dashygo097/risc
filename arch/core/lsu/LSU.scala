package arch.core.lsu

import arch.configs._
import vopts.mem.cache.{ CacheIO, CacheOp }
import chisel3._
import chisel3.util.{ MuxCase, Cat, Fill }

class Lsu(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_lsu"

  val utils = LsuUtilitiesFactory.getOrThrow(p(ISA))

  // Control inputs
  val en            = IO(Input(Bool()))
  val cmd           = IO(Input(UInt(utils.cmdWidth.W)))
  val pma_readable  = IO(Input(Bool()))
  val pma_writable  = IO(Input(Bool()))
  val pma_cacheable = IO(Input(Bool()))

  // Data inputs
  val addr  = IO(Input(UInt(p(XLen).W)))
  val wdata = IO(Input(UInt(p(XLen).W)))

  val mem  = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))
  val mmio = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))

  // Outputs
  val rdata     = IO(Output(UInt(p(XLen).W)))
  val busy      = IO(Output(Bool()))
  val unsigned  = IO(Output(Bool()))
  val mem_read  = IO(Output(Bool()))
  val mem_write = IO(Output(Bool()))

  val rdata_reg     = RegInit(0.U(p(XLen).W))
  val req_fired     = RegInit(false.B)
  val resp_received = RegInit(true.B)

  val byte_offset = addr(1, 0)

  rdata     := 0.U(p(XLen).W)
  unsigned  := en && utils.isUnsigned(cmd)
  mem_read  := utils.isMemRead(en, cmd)
  mem_write := utils.isMemWrite(en, cmd)

  val raw_wdata = MuxCase(
    wdata,
    Seq(
      utils.isByte(cmd) -> Cat(Fill(24, 0.U), wdata(7, 0)),
      utils.isHalf(cmd) -> Cat(Fill(16, 0.U), wdata(15, 0)),
      utils.isWord(cmd) -> wdata,
    )
  )

  val aligned_wdata = (raw_wdata << (byte_offset << 3))(p(XLen) - 1, 0)

  val raw_wmask = MuxCase(
    "b1111".U(4.W),
    Seq(
      utils.isByte(cmd) -> "b0001".U(4.W),
      utils.isHalf(cmd) -> "b0011".U(4.W),
      utils.isWord(cmd) -> "b1111".U(4.W)
    )
  )
  val wmask     = (raw_wmask << byte_offset)(3, 0)

  when(!busy) {
    req_fired := false.B
  }
  when(mem.req.fire) {
    req_fired     := true.B
    resp_received := false.B
  }
  when(mmio.req.fire) {
    req_fired     := true.B
    resp_received := false.B
  }
  when(mem.resp.fire) {
    resp_received := true.B
  }
  when(mmio.resp.fire) {
    resp_received := true.B
  }

  busy := !resp_received || mem.req.fire || mmio.req.fire

  mem.req.valid     := en && !req_fired && pma_cacheable
  mem.req.bits.op   := Mux(mem_write, CacheOp.WRITE, CacheOp.READ)
  mem.req.bits.addr := addr
  mem.req.bits.data := aligned_wdata
  mem.req.bits.strb := wmask
  mem.resp.ready    := pma_cacheable

  mmio.req.valid     := en && !req_fired && !pma_cacheable
  mmio.req.bits.op   := Mux(mem_write, CacheOp.WRITE, CacheOp.READ)
  mmio.req.bits.addr := addr
  mmio.req.bits.data := aligned_wdata
  mmio.req.bits.strb := wmask
  mmio.resp.ready    := !pma_cacheable

  val raw_rdata     = Mux(pma_cacheable, mem.resp.bits.data, mmio.resp.bits.data)
  val shifted_rdata = raw_rdata >> (byte_offset << 3)

  val loaded_data = MuxCase(
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
      utils.isWord(cmd) -> shifted_rdata,
    )
  )

  when(mem.resp.fire || mmio.resp.fire) {
    rdata_reg := loaded_data
  }

  rdata := rdata_reg
}
