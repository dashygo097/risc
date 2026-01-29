package arch.system.bridge

import arch.configs._
import vopts.com.amba._
import vopts.mem.cache._
import chisel3._
import chisel3.util._

object AXIFullBridgeUtilities extends RegisteredUtilities[BusBridgeUtilities] {
  override def utils: BusBridgeUtilities = new BusBridgeUtilities {
    override def name: String = "axif"

    override def busType: Bundle =
      new AXIFullMasterIO(addrWidth = p(XLen), dataWidth = p(XLen), idWidth = 4)

    override def createBridge[T <: Data](gen: T, memory: CacheIO[T]): Bundle = {
      val axi = Wire(new AXIFullMasterIO(addrWidth = p(XLen), dataWidth = p(XLen), idWidth = 4))

      val isWrite = memory.req.bits.op === CacheOp.WRITE
      val isRead  = memory.req.bits.op === CacheOp.READ

      val wordsPerRequest = memory.req.bits.data.getWidth / p(XLen)
      val wordsPerRespond = memory.resp.bits.data.getWidth / p(XLen)

      // Burst length
      val writeBurstLen = (wordsPerRequest - 1).U(8.W)
      val readBurstLen  = (wordsPerRespond - 1).U(8.W)

      val bytesPerWord = p(XLen) / 8

      // Write path
      val w_beat_count = RegInit(0.U(log2Ceil(wordsPerRequest).W))
      val w_active     = RegInit(false.B)
      val w_data_reg   = Reg(UInt(memory.req.bits.data.getWidth.W))

      val w_data_vec = VecInit((0 until wordsPerRequest).map { i =>
        w_data_reg((i + 1) * p(XLen) - 1, i * p(XLen))
      })

      // Start write burst
      when(memory.req.fire && isWrite) {
        w_active     := true.B
        w_beat_count := 0.U
        w_data_reg   := memory.req.bits.data.asUInt
      }.elsewhen(w_active && axi.w.fire) {
        when(w_beat_count === writeBurstLen) {
          w_active := false.B
        }.otherwise {
          w_beat_count := w_beat_count + 1.U
        }
      }

      // AW
      axi.aw.valid       := memory.req.valid && isWrite && !w_active
      axi.aw.bits.addr   := memory.req.bits.addr
      axi.aw.bits.prot   := 0.U
      axi.aw.bits.id     := 0.U
      axi.aw.bits.len    := writeBurstLen
      axi.aw.bits.size   := log2Ceil(bytesPerWord).U
      axi.aw.bits.burst  := 1.U // INCR
      axi.aw.bits.lock   := false.B
      axi.aw.bits.cache  := 0.U
      axi.aw.bits.qos    := 0.U
      axi.aw.bits.region := 0.U
      axi.aw.bits.user   := 0.U

      // W
      axi.w.valid     := w_active
      axi.w.bits.data := w_data_vec(w_beat_count)
      axi.w.bits.strb := Fill(p(XLen) / 8, 1.U)
      axi.w.bits.last := w_beat_count === writeBurstLen
      axi.w.bits.id   := 0.U
      axi.w.bits.user := 0.U

      // B
      axi.b.ready := memory.resp.ready

      // Read path
      val r_beat_count  = RegInit(0.U(log2Ceil(wordsPerRespond).W))
      val r_active      = RegInit(false.B)
      val r_data_buffer = Reg(Vec(wordsPerRespond, UInt(p(XLen).W)))

      // Start read burst
      when(memory.req.fire && isRead) {
        r_active     := true.B
        r_beat_count := 0.U
      }.elsewhen(r_active && axi.r.fire) {
        r_data_buffer(r_beat_count) := axi.r.bits.data
        when(axi.r.bits.last || r_beat_count === readBurstLen) {
          r_active := false.B
        }.otherwise {
          r_beat_count := r_beat_count + 1.U
        }
      }

      // AR
      axi.ar.valid       := memory.req.valid && isRead && !r_active
      axi.ar.bits.addr   := memory.req.bits.addr
      axi.ar.bits.prot   := 0.U
      axi.ar.bits.id     := 0.U
      axi.ar.bits.len    := readBurstLen
      axi.ar.bits.size   := log2Ceil(bytesPerWord).U
      axi.ar.bits.burst  := 1.U // INCR
      axi.ar.bits.lock   := false.B
      axi.ar.bits.cache  := 0.U
      axi.ar.bits.qos    := 0.U
      axi.ar.bits.region := 0.U
      axi.ar.bits.user   := 0.U

      // R
      axi.r.ready := r_active

      // Memory interface
      memory.req.ready := (!w_active && !r_active)

      val w_complete = axi.b.valid && !r_active
      val r_complete = r_active && axi.r.valid && axi.r.bits.last

      memory.resp.valid     := w_complete || r_complete
      memory.resp.bits.data := Cat(r_data_buffer.reverse).asTypeOf(memory.resp.bits.data)
      memory.resp.bits.hit  := true.B

      axi
    }

    override def createBridgeReadOnly[T <: Data](gen: T, memory: CacheReadOnlyIO[T]): Bundle = {
      val axi = Wire(new AXIFullMasterIO(addrWidth = p(XLen), dataWidth = p(XLen), idWidth = 4))

      val wordsPerRespond = memory.resp.bits.data.getWidth / p(XLen)
      val readBurstLen    = (wordsPerRespond - 1).U(8.W)
      val bytesPerWord    = p(XLen) / 8

      // Read path
      val r_beat_count  = RegInit(0.U(log2Ceil(wordsPerRespond).W))
      val r_active      = RegInit(false.B)
      val r_data_buffer = Reg(Vec(wordsPerRespond, UInt(p(XLen).W)))

      // Start read burst
      when(memory.req.fire) {
        r_active     := true.B
        r_beat_count := 0.U
      }.elsewhen(r_active && axi.r.fire) {
        r_data_buffer(r_beat_count) := axi.r.bits.data
        when(axi.r.bits.last || r_beat_count === readBurstLen) {
          r_active := false.B
        }.otherwise {
          r_beat_count := r_beat_count + 1.U
        }
      }

      // AW
      axi.aw.valid       := false.B
      axi.aw.bits.addr   := 0.U
      axi.aw.bits.prot   := 0.U
      axi.aw.bits.id     := 0.U
      axi.aw.bits.len    := 0.U
      axi.aw.bits.size   := 0.U
      axi.aw.bits.burst  := 0.U
      axi.aw.bits.lock   := false.B
      axi.aw.bits.cache  := 0.U
      axi.aw.bits.qos    := 0.U
      axi.aw.bits.region := 0.U
      axi.aw.bits.user   := 0.U

      // W
      axi.w.valid     := false.B
      axi.w.bits.data := 0.U
      axi.w.bits.strb := 0.U
      axi.w.bits.last := false.B
      axi.w.bits.id   := 0.U
      axi.w.bits.user := 0.U

      // B
      axi.b.ready := false.B

      // AR
      axi.ar.valid       := memory.req.valid && !r_active
      axi.ar.bits.addr   := memory.req.bits.addr
      axi.ar.bits.prot   := 0.U
      axi.ar.bits.id     := 0.U
      axi.ar.bits.len    := readBurstLen
      axi.ar.bits.size   := log2Ceil(bytesPerWord).U
      axi.ar.bits.burst  := 1.U // INCR
      axi.ar.bits.lock   := false.B
      axi.ar.bits.cache  := 0.U
      axi.ar.bits.qos    := 0.U
      axi.ar.bits.region := 0.U
      axi.ar.bits.user   := 0.U

      // R
      axi.r.ready := r_active

      // Memory interface
      memory.req.ready := !r_active

      val r_complete = r_active && axi.r.valid && axi.r.bits.last

      memory.resp.valid     := r_complete
      memory.resp.bits.data := Cat(r_data_buffer.reverse).asTypeOf(memory.resp.bits.data)
      memory.resp.bits.hit  := true.B

      axi
    }
  }

  override def factory: UtilitiesFactory[BusBridgeUtilities] = BusBridgeUtilitiesFactory
}
