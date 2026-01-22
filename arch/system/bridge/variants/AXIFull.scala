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

    override def createBridge(memory: UnifiedMemoryIO): Bundle = {
      val axi = Wire(new AXIFullMasterIO(addrWidth = p(XLen), dataWidth = p(XLen), idWidth = 4))

      val isWrite = memory.req.bits.op === MemoryOp.WRITE
      val isRead  = memory.req.bits.op === MemoryOp.READ

      val wordsPerRequest = memory.req.bits.data.getWidth / p(XLen)
      val wordsPerRespond = memory.resp.bits.data.getWidth / p(XLen)

      val writeBurstLen = (wordsPerRequest - 1).U(8.W)
      val readBurstLen  = (wordsPerRespond - 1).U(8.W)

      // Write burst counter
      val w_beat_count = RegInit(0.U(log2Ceil(wordsPerRequest + 1).W))
      val w_active     = RegInit(false.B)

      // Read burst counter
      val r_beat_count = RegInit(0.U(log2Ceil(wordsPerRespond + 1).W))
      val r_active     = RegInit(false.B)

      // AW channel
      axi.aw.valid       := memory.req.valid && isWrite && !w_active
      axi.aw.bits.addr   := memory.req.bits.addr
      axi.aw.bits.prot   := 0.U
      axi.aw.bits.id     := 0.U
      axi.aw.bits.len    := writeBurstLen
      axi.aw.bits.size   := log2Ceil(p(XLen) / 8).U
      axi.aw.bits.burst  := 1.U // INCR
      axi.aw.bits.lock   := false.B
      axi.aw.bits.cache  := 0.U
      axi.aw.bits.qos    := 0.U
      axi.aw.bits.region := 0.U
      axi.aw.bits.user   := 0.U

      // W channel - burst data handling
      when(axi.aw.fire && !w_active) {
        w_active     := true.B
        w_beat_count := 0.U
      }.elsewhen(axi.w.fire) {
        w_beat_count := w_beat_count + 1.U
        when(w_beat_count === writeBurstLen) {
          w_active := false.B
        }
      }

      val w_data_vec = VecInit((0 until wordsPerRequest).map { i =>
        memory.req.bits.data((i + 1) * p(XLen) - 1, i * p(XLen))
      })

      axi.w.valid     := w_active && memory.req.valid && isWrite
      axi.w.bits.data := w_data_vec(w_beat_count)
      axi.w.bits.strb := Fill(p(XLen) / 8, 1.U)
      axi.w.bits.last := w_beat_count === writeBurstLen
      axi.w.bits.id   := 0.U
      axi.w.bits.user := 0.U

      // B channel
      axi.b.ready := memory.resp.ready && !r_active

      // AR channel
      axi.ar.valid       := memory.req.valid && isRead && !r_active
      axi.ar.bits.addr   := memory.req.bits.addr
      axi.ar.bits.prot   := 0.U
      axi.ar.bits.id     := 0.U
      axi.ar.bits.len    := readBurstLen
      axi.ar.bits.size   := log2Ceil(p(XLen) / 8).U
      axi.ar.bits.burst  := 1.U // INCR
      axi.ar.bits.lock   := false.B
      axi.ar.bits.cache  := 0.U
      axi.ar.bits.qos    := 0.U
      axi.ar.bits.region := 0.U
      axi.ar.bits.user   := 0.U

      // R channel - burst data collection
      when(axi.ar.fire) {
        r_active     := true.B
        r_beat_count := 0.U
      }.elsewhen(axi.r.fire) {
        r_beat_count := r_beat_count + 1.U
        when(r_beat_count === readBurstLen || axi.r.bits.last) {
          r_active := false.B
        }
      }

      val r_data_buffer = RegInit(VecInit(Seq.fill(wordsPerRespond)(0.U(p(XLen).W))))

      when(axi.r.fire) {
        r_data_buffer(r_beat_count) := axi.r.bits.data
      }

      axi.r.ready := r_active && memory.resp.ready

      // Request handshake
      val writeAccepted = memory.req.valid && isWrite && axi.aw.ready && !w_active
      val readAccepted  = memory.req.valid && isRead && axi.ar.ready && !r_active

      memory.req.ready := writeAccepted || readAccepted

      // Response
      memory.resp.valid     := Mux(isWrite, axi.b.valid && !r_active, axi.r.valid && axi.r.bits.last)
      memory.resp.bits.data := Cat(r_data_buffer.reverse)

      axi
    }

    override def createBridgeReadOnly(memory: UnifiedMemoryReadOnlyIO): Bundle = {
      val axi = Wire(new AXIFullMasterIO(addrWidth = p(XLen), dataWidth = p(XLen), idWidth = 4))

      val wordsPerRespond = memory.resp.bits.data.getWidth / p(XLen)
      val readBurstLen    = (wordsPerRespond - 1).U(8.W)

      // Read burst counter
      val r_beat_count = RegInit(0.U(log2Ceil(wordsPerRespond + 1).W))
      val r_active     = RegInit(false.B)

      // AW channel - unused
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

      // W channel - unused
      axi.w.valid     := false.B
      axi.w.bits.data := 0.U
      axi.w.bits.strb := 0.U
      axi.w.bits.last := false.B
      axi.w.bits.id   := 0.U
      axi.w.bits.user := 0.U

      // B channel - unused
      axi.b.ready := false.B

      // AR channel
      axi.ar.valid       := memory.req.valid && !r_active
      axi.ar.bits.addr   := memory.req.bits.addr
      axi.ar.bits.prot   := 0.U
      axi.ar.bits.id     := 0.U
      axi.ar.bits.len    := readBurstLen
      axi.ar.bits.size   := log2Ceil(p(XLen) / 8).U
      axi.ar.bits.burst  := 1.U // INCR
      axi.ar.bits.lock   := false.B
      axi.ar.bits.cache  := 0.U
      axi.ar.bits.qos    := 0.U
      axi.ar.bits.region := 0.U
      axi.ar.bits.user   := 0.U

      // R channel - burst data collection
      when(axi.ar.fire) {
        r_active     := true.B
        r_beat_count := 0.U
      }.elsewhen(axi.r.fire) {
        r_beat_count := r_beat_count + 1.U
        when(r_beat_count === readBurstLen || axi.r.bits.last) {
          r_active := false.B
        }
      }

      val r_data_buffer = RegInit(VecInit(Seq.fill(wordsPerRespond)(0.U(p(XLen).W))))

      when(axi.r.fire) {
        r_data_buffer(r_beat_count) := axi.r.bits.data
      }

      axi.r.ready := r_active && memory.resp.ready

      // Request handshake
      val readAccepted = memory.req.valid && axi.ar.ready && !r_active
      memory.req.ready := readAccepted

      // Response
      memory.resp.valid     := axi.r.valid && axi.r.bits.last
      memory.resp.bits.data := Cat(r_data_buffer.reverse)

      axi
    }
  }

  override def factory: UtilitiesFactory[BusBridgeUtilities] = BusBridgeUtilitiesFactory
}
