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

    override def createBridge[T <: Data](gen: T, memory: CacheIO[T]): Bundle = {
      val axi = Wire(new AXILiteMasterIO(addrWidth = p(XLen), dataWidth = p(XLen)))

      val isWrite = memory.req.bits.op === CacheOp.WRITE
      val isRead  = memory.req.bits.op === CacheOp.READ

      val wordsPerRequest = memory.req.bits.data.getWidth / p(XLen)
      val wordsPerRespond = memory.resp.bits.data.getWidth / p(XLen)

      // Write path
      val aw_word_count = RegInit(0.U(log2Ceil(wordsPerRequest).W))
      val aw_active     = RegInit(false.B)
      val w_word_count  = RegInit(0.U(log2Ceil(wordsPerRequest).W))
      val w_active      = RegInit(false.B)
      val w_complete    = RegInit(false.B)
      val w_data_reg    = Reg(UInt(memory.req.bits.data.getWidth.W))
      val w_base_addr   = Reg(UInt(p(XLen).W))

      val w_data_vec = VecInit((0 until wordsPerRequest).map { i =>
        w_data_reg((i + 1) * p(XLen) - 1, i * p(XLen))
      })

      // Start write transaction
      when(memory.req.fire && isWrite) {
        aw_word_count := 0.U
        aw_active     := true.B
        w_word_count  := 0.U
        w_active      := true.B
        w_complete    := false.B
        w_data_reg    := memory.req.bits.data.asUInt
        w_base_addr   := memory.req.bits.addr
      }

      // AW channel
      when(aw_active && axi.aw.fire) {
        aw_word_count := aw_word_count + 1.U
        when(aw_word_count === (wordsPerRequest - 1).U) {
          aw_active := false.B
        }
      }
      axi.aw.valid     := aw_active
      axi.aw.bits.addr := w_base_addr + Cat(aw_word_count, 0.U(log2Ceil(p(XLen) / 8).W))
      axi.aw.bits.prot := 0.U

      // W channel
      when(w_active && axi.w.fire) {
        w_word_count := w_word_count + 1.U
        when(w_word_count === (wordsPerRequest - 1).U) {
          w_active := false.B
        }
      }
      axi.w.valid     := w_active
      axi.w.bits.data := w_data_vec(w_word_count)
      axi.w.bits.strb := Fill(p(XLen) / 8, 1.U)

      // B channel
      val b_count  = RegInit(0.U(log2Ceil(wordsPerRequest).W))
      val b_active = RegInit(false.B)

      when(memory.req.fire && isWrite) {
        b_count  := 0.U
        b_active := true.B
      }.elsewhen(b_active && axi.b.fire) {
        b_count := b_count + 1.U
        when(b_count === (wordsPerRequest - 1).U) {
          b_active   := false.B
          w_complete := true.B
        }
      }.elsewhen(w_complete) {
        w_complete := false.B
      }
      axi.b.ready := b_active

      // Read path
      val ar_word_count = RegInit(0.U(log2Ceil(wordsPerRespond).W))
      val ar_active     = RegInit(false.B)
      val ar_base_addr  = Reg(UInt(p(XLen).W))
      val r_word_count  = RegInit(0.U(log2Ceil(wordsPerRespond).W))
      val r_active      = RegInit(false.B)
      val r_complete    = RegInit(false.B)
      val r_data_buffer = Reg(Vec(wordsPerRespond, UInt(p(XLen).W)))

      // Start read transaction
      when(memory.req.fire && isRead) {
        ar_word_count := 0.U
        ar_active     := true.B
        ar_base_addr  := memory.req.bits.addr
        r_word_count  := 0.U
        r_active      := true.B
        r_complete    := false.B
      }

      // AR channel
      when(ar_active && axi.ar.fire) {
        ar_word_count := ar_word_count + 1.U
        when(ar_word_count === (wordsPerRespond - 1).U) {
          ar_active := false.B
        }
      }
      axi.ar.valid     := ar_active
      axi.ar.bits.addr := ar_base_addr + Cat(ar_word_count, 0.U(log2Ceil(p(XLen) / 8).W))
      axi.ar.bits.prot := 0.U

      // R channel
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
      memory.req.ready := (!aw_active && !w_active && !b_active && !ar_active && !r_active)

      memory.resp.valid     := w_complete || r_complete
      memory.resp.bits.data := Cat(r_data_buffer.reverse).asTypeOf(memory.resp.bits.data)
      memory.resp.bits.hit  := true.B

      axi
    }

    override def createBridgeReadOnly[T <: Data](gen: T, memory: CacheReadOnlyIO[T]): Bundle = {
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
      memory.resp.bits.data := Cat(r_data_buffer.reverse).asTypeOf(memory.resp.bits.data)
      memory.resp.bits.hit  := true.B

      axi
    }
  }

  override def factory: UtilitiesFactory[BusBridgeUtilities] = BusBridgeUtilitiesFactory
}
