package arch.system.bridge

import arch.configs._
import vopts.com.amba._
import vopts.mem.cache._
import chisel3._
import chisel3.util.{ log2Ceil, Cat }

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

      val writeBurstLen = (wordsPerRequest - 1).U(8.W)
      val readBurstLen  = (wordsPerRespond - 1).U(8.W)
      val bytesPerWord  = p(XLen) / 8

      val w_blockMask = ~((wordsPerRequest * bytesPerWord) - 1).U(p(XLen).W)
      val r_blockMask = ~((wordsPerRespond * bytesPerWord) - 1).U(p(XLen).W)

      val req_addr = RegInit(0.U(p(XLen).W))

      val w_data_reg = RegInit(0.U(memory.req.bits.data.getWidth.W))
      val w_strb_reg = RegInit(0.U(memory.req.bits.strb.getWidth.W))

      val active_write = RegInit(false.B)
      val active_read  = RegInit(false.B)

      val aw_sent = RegInit(false.B)
      val ar_sent = RegInit(false.B)

      val w_beat_count = RegInit(0.U(log2Ceil(wordsPerRequest + 1).W))
      val r_beat_count = RegInit(0.U(log2Ceil(wordsPerRespond + 1).W))

      val r_data_buffer = Reg(Vec(wordsPerRespond, UInt(p(XLen).W)))

      memory.req.ready := !active_write && !active_read

      val is_new_req = memory.req.valid && !active_read && !active_write

      val w_start_addr = memory.req.bits.addr & w_blockMask
      val r_start_addr = memory.req.bits.addr & r_blockMask

      when(is_new_req) {
        when(isWrite) {
          req_addr     := w_start_addr
          active_write := true.B
          aw_sent      := axi.aw.ready
          w_beat_count := 0.U
          w_data_reg   := memory.req.bits.data.asUInt
          w_strb_reg   := memory.req.bits.strb
        }.elsewhen(isRead) {
          req_addr     := r_start_addr
          active_read  := true.B
          ar_sent      := axi.ar.ready
          r_beat_count := 0.U
        }
      }

      // Write Logic
      when(active_write && !aw_sent && axi.aw.fire) {
        aw_sent := true.B
      }

      val w_last               = w_beat_count === writeBurstLen
      val current_active_write = active_write || (is_new_req && isWrite)

      when(current_active_write && axi.w.fire) {
        w_beat_count := w_beat_count + 1.U
      }

      when(active_write && axi.b.fire) {
        active_write := false.B
      }

      // AW
      axi.aw.valid       := (is_new_req && isWrite) || (active_write && !aw_sent)
      axi.aw.bits.addr   := Mux(is_new_req, w_start_addr, req_addr) // Block aligned
      axi.aw.bits.prot   := 0.U
      axi.aw.bits.id     := 0.U
      axi.aw.bits.len    := writeBurstLen
      axi.aw.bits.size   := log2Ceil(bytesPerWord).U
      axi.aw.bits.burst  := 1.U                                     // INCR
      axi.aw.bits.lock   := false.B
      axi.aw.bits.cache  := 0.U
      axi.aw.bits.qos    := 0.U
      axi.aw.bits.region := 0.U
      axi.aw.bits.user   := 0.U

      val w_in_bounds = w_beat_count < wordsPerRequest.U
      val send_data   = Mux(is_new_req, memory.req.bits.data.asUInt, w_data_reg)
      val send_strb   = Mux(is_new_req, memory.req.bits.strb, w_strb_reg)

      val rt_data_vec = VecInit((0 until wordsPerRequest).map(i => send_data((i + 1) * p(XLen) - 1, i * p(XLen))))
      val rt_strb_vec = VecInit((0 until wordsPerRequest).map(i => send_strb((i + 1) * bytesPerWord - 1, i * bytesPerWord)))

      axi.w.valid     := current_active_write && w_in_bounds
      axi.w.bits.data := rt_data_vec(Mux(w_in_bounds, w_beat_count, 0.U))
      axi.w.bits.strb := rt_strb_vec(Mux(w_in_bounds, w_beat_count, 0.U))
      axi.w.bits.last := w_last
      axi.w.bits.id   := 0.U
      axi.w.bits.user := 0.U

      axi.b.ready := active_write || is_new_req

      // Read Logic
      when(active_read && !ar_sent && axi.ar.fire) {
        ar_sent := true.B
      }

      val r_last              = axi.r.bits.last || (r_beat_count === readBurstLen)
      val current_active_read = active_read || (is_new_req && isRead)

      when(current_active_read && axi.r.fire) {
        when(r_beat_count < wordsPerRespond.U) {
          r_data_buffer(r_beat_count) := axi.r.bits.data
        }
        when(!r_last) {
          r_beat_count := r_beat_count + 1.U
        }
      }

      val r_complete = current_active_read && axi.r.fire && r_last
      when(r_complete) {
        active_read := false.B
      }

      axi.ar.valid       := (is_new_req && isRead) || (active_read && !ar_sent)
      axi.ar.bits.addr   := Mux(is_new_req, r_start_addr, req_addr) // Block aligned
      axi.ar.bits.prot   := 0.U
      axi.ar.bits.id     := 0.U
      axi.ar.bits.len    := readBurstLen
      axi.ar.bits.size   := log2Ceil(bytesPerWord).U
      axi.ar.bits.burst  := 1.U                                     // INCR
      axi.ar.bits.lock   := false.B
      axi.ar.bits.cache  := 0.U
      axi.ar.bits.qos    := 0.U
      axi.ar.bits.region := 0.U
      axi.ar.bits.user   := 0.U

      axi.r.ready := current_active_read

      // Memory Response
      val w_complete = active_write && axi.b.fire
      memory.resp.valid := w_complete || r_complete

      val final_data_vec = Wire(Vec(wordsPerRespond, UInt(p(XLen).W)))
      for (i <- 0 until wordsPerRespond)
        final_data_vec(i) := Mux(current_active_read && axi.r.fire && r_beat_count === i.U, axi.r.bits.data, r_data_buffer(i))

      memory.resp.bits.data := Cat(final_data_vec.reverse).asTypeOf(memory.resp.bits.data)
      memory.resp.bits.hit  := true.B

      axi
    }

    override def createBridgeReadOnly[T <: Data](gen: T, memory: CacheReadOnlyIO[T]): Bundle = {
      val axi = Wire(new AXIFullMasterIO(addrWidth = p(XLen), dataWidth = p(XLen), idWidth = 4))

      val wordsPerRespond = memory.resp.bits.data.getWidth / p(XLen)
      val readBurstLen    = (wordsPerRespond - 1).U(8.W)
      val bytesPerWord    = p(XLen) / 8

      val blockMask = ~((wordsPerRespond * bytesPerWord) - 1).U(p(XLen).W)

      val req_addr    = RegInit(0.U(p(XLen).W))
      val active_read = RegInit(false.B)
      val ar_sent     = RegInit(false.B)

      val r_beat_count  = RegInit(0.U(log2Ceil(wordsPerRespond + 1).W))
      val r_data_buffer = Reg(Vec(wordsPerRespond, UInt(p(XLen).W)))

      memory.req.ready := !active_read

      val is_new_req = memory.req.valid && !active_read
      val start_addr = memory.req.bits.addr & blockMask // Block aligned

      when(is_new_req) {
        req_addr     := start_addr
        active_read  := true.B
        ar_sent      := axi.ar.ready
        r_beat_count := 0.U
      }.elsewhen(active_read && !ar_sent && axi.ar.fire) {
        ar_sent := true.B
      }

      val r_last              = axi.r.bits.last || (r_beat_count === readBurstLen)
      val current_active_read = active_read || is_new_req

      when(current_active_read && axi.r.fire) {
        when(r_beat_count < wordsPerRespond.U) {
          r_data_buffer(r_beat_count) := axi.r.bits.data
        }
        when(!r_last) {
          r_beat_count := r_beat_count + 1.U
        }
      }

      val r_complete = current_active_read && axi.r.fire && r_last
      when(r_complete) {
        active_read := false.B
      }

      axi.aw.valid    := false.B
      axi.aw.bits     := DontCare
      axi.w.valid     := false.B
      axi.w.bits      := DontCare
      axi.w.bits.strb := 0.U
      axi.b.ready     := false.B

      axi.ar.valid       := is_new_req || (active_read && !ar_sent)
      axi.ar.bits.addr   := Mux(is_new_req, start_addr, req_addr) // Block aligned
      axi.ar.bits.prot   := 0.U
      axi.ar.bits.id     := 0.U
      axi.ar.bits.len    := readBurstLen
      axi.ar.bits.size   := log2Ceil(bytesPerWord).U
      axi.ar.bits.burst  := 1.U                                   // INCR
      axi.ar.bits.lock   := false.B
      axi.ar.bits.cache  := 0.U
      axi.ar.bits.qos    := 0.U
      axi.ar.bits.region := 0.U
      axi.ar.bits.user   := 0.U

      axi.r.ready := current_active_read

      memory.resp.valid := r_complete

      val final_data_vec = Wire(Vec(wordsPerRespond, UInt(p(XLen).W)))
      for (i <- 0 until wordsPerRespond)
        final_data_vec(i) := Mux(current_active_read && axi.r.fire && r_beat_count === i.U, axi.r.bits.data, r_data_buffer(i))

      memory.resp.bits.data := Cat(final_data_vec.reverse).asTypeOf(memory.resp.bits.data)
      memory.resp.bits.hit  := true.B

      axi
    }
  }

  override def factory: UtilitiesFactory[BusBridgeUtilities] = BusBridgeUtilitiesFactory
}
