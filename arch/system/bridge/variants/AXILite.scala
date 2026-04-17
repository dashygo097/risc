package arch.system.bridge

import arch.configs._
import vopts.com.amba._
import vopts.mem.cache._
import chisel3._
import chisel3.util.{ log2Ceil, Cat, switch, is }

object AXIBridgeState extends ChiselEnum {
  val IDLE, AR, R, AW, W, B = Value
}

object AXILiteBridgeUtilities extends RegisteredUtilities[BusBridgeUtilities] {
  override def utils: BusBridgeUtilities = new BusBridgeUtilities {
    override def name: String = "axil"

    override def busType: Bundle =
      new AXILiteMasterIO(addrWidth = p(XLen), dataWidth = p(XLen))

    override def createBridge[T <: Data](gen: T, memory: CacheIO[T], isMmio: Boolean = false): Bundle = {
      val axi = Wire(new AXILiteMasterIO(addrWidth = p(XLen), dataWidth = p(XLen)))

      val bytesPerAxiBeat = p(XLen) / 8
      val bytesPerGen     = memory.req.bits.data.getWidth / 8
      val axiBeatsPerGen  = bytesPerGen / bytesPerAxiBeat

      val wordsPerLine  = if (isMmio) 1 else p(L1DCacheLineSize) / bytesPerGen
      val totalAxiBeats = wordsPerLine * axiBeatsPerGen

      val state    = RegInit(AXIBridgeState.IDLE)
      val req_addr = RegInit(0.U(p(XLen).W))

      val r_beat_count  = RegInit(0.U(log2Ceil(totalAxiBeats + 1).max(1).W))
      val r_pack_count  = RegInit(0.U(log2Ceil(axiBeatsPerGen + 1).max(1).W))
      val r_data_buffer = Reg(Vec(axiBeatsPerGen.max(1), UInt(p(XLen).W)))

      val w_beat_count   = RegInit(0.U(log2Ceil(totalAxiBeats + 1).max(1).W))
      val w_unpack_count = RegInit(0.U(log2Ceil(axiBeatsPerGen + 1).max(1).W))
      val w_data_buffer  = Reg(UInt(memory.req.bits.data.getWidth.max(p(XLen)).W))
      val w_strb_buffer  = Reg(UInt(memory.req.bits.strb.getWidth.max(p(XLen) / 8).W))

      axi.aw.valid := false.B; axi.aw.bits := DontCare
      axi.w.valid  := false.B; axi.w.bits  := DontCare
      axi.b.ready  := false.B
      axi.ar.valid := false.B; axi.ar.bits := DontCare
      axi.r.ready  := false.B

      memory.req.ready      := false.B
      memory.resp.valid     := false.B
      memory.resp.bits.data := DontCare
      memory.resp.bits.last := false.B
      memory.resp.bits.hit  := false.B

      switch(state) {
        is(AXIBridgeState.IDLE) {
          memory.req.ready := true.B
          when(memory.req.fire) {
            req_addr := memory.req.bits.addr
            when(memory.req.bits.op === CacheOp.READ) {
              state        := AXIBridgeState.AR
              r_beat_count := 0.U
              r_pack_count := 0.U
            }.otherwise {
              state          := AXIBridgeState.AW
              w_beat_count   := 0.U
              w_unpack_count := 0.U
              w_data_buffer  := memory.req.bits.data.asUInt
              w_strb_buffer  := memory.req.bits.strb
            }
          }
        }

        is(AXIBridgeState.AR) {
          val isWrap        = !isMmio.B
          val wrapBytes     = totalAxiBeats * bytesPerAxiBeat
          val wrapMask      = (wrapBytes - 1).U(p(XLen).W)
          val alignedBase   = req_addr & ~wrapMask
          val currentOffset = (req_addr & wrapMask) + (r_beat_count * bytesPerAxiBeat.U)
          val wrappedOffset = currentOffset & wrapMask

          val ar_addr = Mux(isWrap, alignedBase | wrappedOffset, req_addr + (r_beat_count * bytesPerAxiBeat.U))

          axi.ar.valid     := true.B
          axi.ar.bits.addr := ar_addr
          axi.ar.bits.prot := 0.U

          when(axi.ar.fire)(state := AXIBridgeState.R)
        }
        is(AXIBridgeState.R) {
          val is_last_pack = r_pack_count === (axiBeatsPerGen - 1).U
          val is_last_beat = r_beat_count === (totalAxiBeats - 1).U

          memory.resp.valid     := axi.r.valid && is_last_pack
          memory.resp.bits.last := is_last_beat

          val final_data_vec = Wire(Vec(axiBeatsPerGen, UInt(p(XLen).W)))
          for (i <- 0 until axiBeatsPerGen)
            final_data_vec(i) := Mux(i.U === r_pack_count, axi.r.bits.data, r_data_buffer(i))
          memory.resp.bits.data := Cat(final_data_vec.reverse).asTypeOf(gen)

          axi.r.ready := Mux(is_last_pack, memory.resp.ready, true.B)

          when(axi.r.fire) {
            when(!is_last_pack) {
              r_data_buffer(r_pack_count) := axi.r.bits.data
            }
            r_pack_count := Mux(is_last_pack, 0.U, r_pack_count + 1.U)
            r_beat_count := r_beat_count + 1.U

            when(is_last_beat) {
              state := AXIBridgeState.IDLE
            }.otherwise {
              state := AXIBridgeState.AR
            }
          }
        }

        is(AXIBridgeState.AW) {
          axi.aw.valid     := true.B
          axi.aw.bits.addr := req_addr + (w_beat_count * bytesPerAxiBeat.U)
          axi.aw.bits.prot := 0.U

          when(axi.aw.fire)(state := AXIBridgeState.W)
        }
        is(AXIBridgeState.W) {
          axi.w.valid     := true.B
          axi.w.bits.data := w_data_buffer(p(XLen) - 1, 0)
          axi.w.bits.strb := w_strb_buffer(bytesPerAxiBeat - 1, 0)

          val is_last_unpack       = w_unpack_count === (axiBeatsPerGen - 1).U
          val is_last_overall_word = w_beat_count === (totalAxiBeats - 1).U

          memory.req.ready := axi.w.ready && is_last_unpack && !is_last_overall_word

          when(axi.w.fire) {
            w_unpack_count := Mux(is_last_unpack, 0.U, w_unpack_count + 1.U)

            when(is_last_unpack) {
              w_data_buffer := memory.req.bits.data.asUInt
              w_strb_buffer := memory.req.bits.strb
            }.otherwise {
              w_data_buffer := w_data_buffer >> p(XLen)
              w_strb_buffer := w_strb_buffer >> bytesPerAxiBeat
            }
            state := AXIBridgeState.B
          }
        }
        is(AXIBridgeState.B) {
          val is_last_beat = w_beat_count === (totalAxiBeats - 1).U

          memory.resp.valid     := axi.b.valid && is_last_beat
          memory.resp.bits.last := true.B

          axi.b.ready := Mux(is_last_beat, memory.resp.ready, true.B)

          when(axi.b.fire) {
            w_beat_count := w_beat_count + 1.U
            when(is_last_beat) {
              state := AXIBridgeState.IDLE
            }.otherwise {
              state := AXIBridgeState.AW
            }
          }
        }
      }
      axi
    }

    override def createBridgeReadOnly[T <: Data](gen: T, memory: CacheReadOnlyIO[T], isMmio: Boolean = false): Bundle = {
      val axi = Wire(new AXILiteMasterIO(addrWidth = p(XLen), dataWidth = p(XLen)))

      val bytesPerAxiBeat = p(XLen) / 8
      val bytesPerGen     = memory.resp.bits.data.getWidth / 8
      val axiBeatsPerGen  = bytesPerGen / bytesPerAxiBeat

      val wordsPerLine  = if (isMmio) 1 else p(L1ICacheLineSize) / bytesPerGen
      val totalAxiBeats = wordsPerLine * axiBeatsPerGen

      val state    = RegInit(AXIBridgeState.IDLE)
      val req_addr = RegInit(0.U(p(XLen).W))

      val r_beat_count  = RegInit(0.U(log2Ceil(totalAxiBeats + 1).max(1).W))
      val r_pack_count  = RegInit(0.U(log2Ceil(axiBeatsPerGen + 1).max(1).W))
      val r_data_buffer = Reg(Vec(axiBeatsPerGen.max(1), UInt(p(XLen).W)))

      axi.aw.valid := false.B; axi.aw.bits := DontCare
      axi.w.valid  := false.B; axi.w.bits  := DontCare
      axi.b.ready  := false.B
      axi.ar.valid := false.B; axi.ar.bits := DontCare
      axi.r.ready  := false.B

      memory.req.ready      := false.B
      memory.resp.valid     := false.B
      memory.resp.bits.data := DontCare
      memory.resp.bits.last := false.B
      memory.resp.bits.hit  := false.B

      switch(state) {
        is(AXIBridgeState.IDLE) {
          memory.req.ready := true.B
          when(memory.req.fire) {
            req_addr     := memory.req.bits.addr
            state        := AXIBridgeState.AR
            r_beat_count := 0.U
            r_pack_count := 0.U
          }
        }

        is(AXIBridgeState.AR) {
          val isWrap        = !isMmio.B
          val wrapBytes     = totalAxiBeats * bytesPerAxiBeat
          val wrapMask      = (wrapBytes - 1).U(p(XLen).W)
          val alignedBase   = req_addr & ~wrapMask
          val currentOffset = (req_addr & wrapMask) + (r_beat_count * bytesPerAxiBeat.U)
          val wrappedOffset = currentOffset & wrapMask

          val ar_addr = Mux(isWrap, alignedBase | wrappedOffset, req_addr + (r_beat_count * bytesPerAxiBeat.U))

          axi.ar.valid     := true.B
          axi.ar.bits.addr := ar_addr
          axi.ar.bits.prot := 0.U

          when(axi.ar.fire)(state := AXIBridgeState.R)
        }

        is(AXIBridgeState.R) {
          val is_last_pack = r_pack_count === (axiBeatsPerGen - 1).U
          val is_last_beat = r_beat_count === (totalAxiBeats - 1).U

          memory.resp.valid     := axi.r.valid && is_last_pack
          memory.resp.bits.last := is_last_beat

          val final_data_vec = Wire(Vec(axiBeatsPerGen, UInt(p(XLen).W)))
          for (i <- 0 until axiBeatsPerGen)
            final_data_vec(i) := Mux(i.U === r_pack_count, axi.r.bits.data, r_data_buffer(i))
          memory.resp.bits.data := Cat(final_data_vec.reverse).asTypeOf(gen)

          axi.r.ready := Mux(is_last_pack, memory.resp.ready, true.B)

          when(axi.r.fire) {
            when(!is_last_pack) {
              r_data_buffer(r_pack_count) := axi.r.bits.data
            }
            r_pack_count := Mux(is_last_pack, 0.U, r_pack_count + 1.U)
            r_beat_count := r_beat_count + 1.U

            when(is_last_beat) {
              state := AXIBridgeState.IDLE
            }.otherwise {
              state := AXIBridgeState.AR
            }
          }
        }
      }
      axi
    }
  }

  override def factory: UtilitiesFactory[BusBridgeUtilities] = BusBridgeUtilitiesFactory
}
