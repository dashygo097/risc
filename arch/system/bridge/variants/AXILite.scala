package arch.system.bridge

import arch.configs._
import vopts.com.amba._
import vopts.mem.cache._
import chisel3._
import chisel3.util._

object AXILiteBridgeUtilities extends RegisteredUtilities[BusBridgeUtilities] {
  override def utils: BusBridgeUtilities = new BusBridgeUtilities {
    override def name: String = "axil"

    override def busType: Bundle =
      new AXILiteMasterIO(addrWidth = p(XLen), dataWidth = p(XLen))

    override def createBridge(memory: UnifiedMemoryIO): Bundle = {
      val axi = Wire(new AXILiteMasterIO(addrWidth = p(XLen), dataWidth = p(XLen)))

      val isWrite = memory.req.bits.op === MemoryOp.WRITE
      val isRead  = memory.req.bits.op === MemoryOp.READ

      val wordsPerRequest = memory.req.bits.data.getWidth / p(XLen)
      val wordsPerRespond = memory.resp.bits.data.getWidth / p(XLen)

      // Write path
      val w_word_count = RegInit(0.U(log2Ceil(wordsPerRequest).W))
      val w_active     = RegInit(false.B)
      val w_data_reg   = Reg(UInt(memory.req.bits.data.getWidth.W))
      val w_addr_reg   = Reg(UInt(p(XLen).W))

      val w_data_vec = VecInit((0 until wordsPerRequest).map { i =>
        w_data_reg((i + 1) * p(XLen) - 1, i * p(XLen))
      })

      // Start write transaction
      when(memory.req.fire && isWrite) {
        w_active     := true.B
        w_word_count := 0.U
        w_data_reg   := memory.req.bits.data
        w_addr_reg   := memory.req.bits.addr
      }.elsewhen(w_active && axi.aw.fire && axi.w.fire) {
        when(w_word_count === (wordsPerRequest - 1).U) {
          w_active := false.B
        }.otherwise {
          w_word_count := w_word_count + 1.U
        }
      }

      // AW
      axi.aw.valid     := w_active
      axi.aw.bits.addr := w_addr_reg + Cat(w_word_count, 0.U(log2Ceil(p(XLen) / 8).W))
      axi.aw.bits.prot := 0.U

      // W
      axi.w.valid     := w_active
      axi.w.bits.data := w_data_vec(w_word_count)
      axi.w.bits.strb := Fill(p(XLen) / 8, 1.U)

      // B
      val w_last_transfer = w_word_count === (wordsPerRequest - 1).U
      axi.b.ready := w_active && w_last_transfer

      // Read path
      val r_word_count  = RegInit(0.U(log2Ceil(wordsPerRespond).W))
      val r_active      = RegInit(false.B)
      val r_data_buffer = Reg(Vec(wordsPerRespond, UInt(p(XLen).W)))
      val r_addr_reg    = Reg(UInt(p(XLen).W))

      // Start read transaction
      when(memory.req.fire && isRead) {
        r_active     := true.B
        r_word_count := 0.U
        r_addr_reg   := memory.req.bits.addr
      }.elsewhen(r_active && axi.r.fire) {
        r_data_buffer(r_word_count) := axi.r.bits.data
        when(r_word_count === (wordsPerRespond - 1).U) {
          r_active := false.B
        }.otherwise {
          r_word_count := r_word_count + 1.U
        }
      }

      // AR
      axi.ar.valid     := r_active
      axi.ar.bits.addr := r_addr_reg + Cat(r_word_count, 0.U(log2Ceil(p(XLen) / 8).W))
      axi.ar.bits.prot := 0.U

      // R
      axi.r.ready := r_active

      // Memory interface
      memory.req.ready := (!w_active && !r_active)

      val w_complete = w_active && w_last_transfer && axi.b.valid
      val r_complete = r_active && (r_word_count === (wordsPerRespond - 1).U) && axi.r.valid

      memory.resp.valid     := w_complete || r_complete
      memory.resp.bits.data := Cat(r_data_buffer.reverse)

      axi
    }

    override def createBridgeReadOnly(memory: UnifiedMemoryReadOnlyIO): Bundle = {
      val axi = Wire(new AXILiteMasterIO(addrWidth = p(XLen), dataWidth = p(XLen)))

      val wordsPerRespond = memory.resp.bits.data.getWidth / p(XLen)

      // Read path
      val ar_word_count = RegInit(0.U(log2Ceil(wordsPerRespond).W))
      val ar_active     = RegInit(false.B)
      val ar_base_addr  = Reg(UInt(p(XLen).W))
      val r_word_count  = RegInit(0.U(log2Ceil(wordsPerRespond).W))
      val r_active      = RegInit(false.B)
      val r_complete    = RegInit(false.B)
      val r_data_buffer = Reg(Vec(wordsPerRespond, UInt(p(XLen).W)))

      // Start read transaction
      when(memory.req.fire) {
        ar_word_count := 0.U
        ar_active     := true.B
        ar_base_addr  := memory.req.bits.addr
        r_word_count  := 0.U
        r_active      := true.B
        r_complete    := false.B
      }

      // AW
      axi.aw.valid     := false.B
      axi.aw.bits.addr := 0.U
      axi.aw.bits.prot := 0.U

      // W
      axi.w.valid     := false.B
      axi.w.bits.data := 0.U
      axi.w.bits.strb := 0.U

      // B
      axi.b.ready := false.B

      // AR
      when(ar_active && axi.ar.fire) {
        ar_word_count := ar_word_count + 1.U
        when(ar_word_count === (wordsPerRespond - 1).U) {
          ar_active := false.B
        }
      }
      axi.ar.valid     := ar_active
      axi.ar.bits.addr := ar_base_addr + Cat(ar_word_count, 0.U(log2Ceil(p(XLen) / 8).W))
      axi.ar.bits.prot := 0.U

      // R
      when(r_active && axi.r.fire) {
        r_data_buffer(r_word_count) := axi.r.bits.data
        r_word_count                := r_word_count + 1.U
        when(r_word_count === (wordsPerRespond - 1).U) {
          r_complete := true.B
          r_active   := false.B
        }
      }.elsewhen(r_complete) {
        r_complete := false.B
      }
      axi.r.ready := r_active

      // Memory interface
      memory.req.ready := !r_active

      memory.resp.valid     := r_complete
      memory.resp.bits.data := Cat(r_data_buffer.reverse)

      axi
    }
  }

  override def factory: UtilitiesFactory[BusBridgeUtilities] = BusBridgeUtilitiesFactory
}
