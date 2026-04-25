package arch.core.lsu

import arch.configs._
import vopts.mem.cache.{ CacheIO, CacheOp }
import chisel3._
import chisel3.util.{ MuxCase, Cat, Fill, log2Ceil }

class LsuCtrl extends Bundle {
  val is_byte     = Bool()
  val is_half     = Bool()
  val is_word     = Bool()
  val is_dword    = Bool()
  val is_unsigned = Bool()
  val is_read     = Bool()
  val is_write    = Bool()
  val strb        = UInt((p(XLen) / 8).W)
}

class Lsu(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_lsu"

  val utils           = LsuUtilsFactory.getOrThrow(p(ISA).name)
  val byteOffset      = p(XLen) / 8
  val byteOffsetWidth = log2Ceil(byteOffset)

  // Control inputs
  val en            = IO(Input(Bool()))
  val uop           = IO(Input(UInt(p(MicroOpWidth).W)))
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

  val ctrl = utils.decode(uop)

  rdata     := 0.U(p(XLen).W)
  unsigned  := en && ctrl.is_unsigned
  mem_read  := en && ctrl.is_read
  mem_write := en && ctrl.is_write

  val raw_wdata = MuxCase(
    wdata,
    Seq(
      ctrl.is_byte  -> Cat(Fill(p(XLen) - 8, 0.U), wdata(7, 0)),
      ctrl.is_half  -> Cat(Fill(p(XLen) - 16, 0.U), wdata(15, 0)),
      ctrl.is_word  -> (if (p(XLen) == 64) Cat(Fill(p(XLen) - 32, 0.U), wdata(31, 0)) else wdata),
      ctrl.is_dword -> wdata
    )
  )

  val byte_offset      = addr(byteOffsetWidth - 1, 0)
  val aligned_wdata    = (raw_wdata << (byte_offset << 3))(p(XLen) - 1, 0)
  val aligned_bus_addr = Cat(addr(p(XLen) - 1, byteOffsetWidth), 0.U(byteOffsetWidth.W))

  val raw_wmask = MuxCase(
    Fill(p(XLen) / 8, 1.U).asUInt,
    Seq(
      ctrl.is_byte  -> "b0001".U((p(XLen) / 8).W),
      ctrl.is_half  -> "b0011".U((p(XLen) / 8).W),
      ctrl.is_word  -> "b1111".U((p(XLen) / 8).W),
      ctrl.is_dword -> Fill(8, 1.U).asUInt
    )
  )

  val wmask = (raw_wmask << byte_offset)(p(XLen) / 8 - 1, 0)

  when(!busy) {
    req_fired := false.B
  }
  when(mem.req.fire || mmio.req.fire) {
    req_fired     := true.B
    resp_received := false.B
  }
  when(mem.resp.fire || mmio.resp.fire) {
    resp_received := true.B
  }

  busy := !resp_received || mem.req.fire || mmio.req.fire

  mem.req.valid     := en && !req_fired && pma_cacheable
  mem.req.bits.op   := Mux(mem_write, CacheOp.WRITE, CacheOp.READ)
  mem.req.bits.addr := aligned_bus_addr
  mem.req.bits.data := aligned_wdata
  mem.req.bits.strb := wmask
  mem.resp.ready    := pma_cacheable

  mmio.req.valid     := en && !req_fired && !pma_cacheable
  mmio.req.bits.op   := Mux(mem_write, CacheOp.WRITE, CacheOp.READ)
  mmio.req.bits.addr := aligned_bus_addr
  mmio.req.bits.data := aligned_wdata
  mmio.req.bits.strb := wmask
  mmio.resp.ready    := !pma_cacheable

  val raw_rdata     = Mux(pma_cacheable, mem.resp.bits.data, mmio.resp.bits.data)
  val shifted_rdata = raw_rdata >> (byte_offset << 3)

  val loaded_data = MuxCase(
    shifted_rdata,
    Seq(
      ctrl.is_byte  -> Cat(
        Fill(p(XLen) - 8, !ctrl.is_unsigned && shifted_rdata(7)),
        shifted_rdata(7, 0)
      ),
      ctrl.is_half  -> Cat(
        Fill(p(XLen) - 16, !ctrl.is_unsigned && shifted_rdata(15)),
        shifted_rdata(15, 0)
      ),
      ctrl.is_word  -> (if (p(XLen) == 64)
                         Cat(
                           Fill(p(XLen) - 32, !ctrl.is_unsigned && shifted_rdata(31)),
                           shifted_rdata(31, 0)
                         )
                       else shifted_rdata),
      ctrl.is_dword -> shifted_rdata
    )
  )

  when(mem.resp.fire || mmio.resp.fire) {
    rdata_reg := loaded_data
  }

  rdata := rdata_reg
}
