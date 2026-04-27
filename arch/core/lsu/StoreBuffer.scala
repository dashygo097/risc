package arch.core.lsu

import arch.configs._
import vopts.mem.cache._
import chisel3._
import chisel3.util._

class StoreAllocBundle(implicit p: Parameters) extends Bundle {
  val sq_idx  = UInt(log2Ceil(p(StoreBufferSize)).W)
  val sq_seq  = UInt(64.W)
  val rob_tag = UInt(log2Ceil(p(ROBSize)).W)
}

class StoreWriteBundle(implicit p: Parameters) extends Bundle {
  val sq_idx    = UInt(log2Ceil(p(StoreBufferSize)).W)
  val rob_tag   = UInt(log2Ceil(p(ROBSize)).W)
  val addr      = UInt(p(XLen).W)
  val data      = UInt(p(XLen).W)
  val mask      = UInt((p(XLen) / 8).W)
  val cacheable = Bool()
}

class StoreForwardReq(implicit p: Parameters) extends Bundle {
  val valid  = Bool()
  val sq_seq = UInt(64.W)
  val addr   = UInt(p(XLen).W)
  val mask   = UInt((p(XLen) / 8).W)
}

class StoreForwardResp(implicit p: Parameters) extends Bundle {
  val block    = Bool()
  val hasOlder = Bool()

  val fwdValid = Bool()
  val fwdFull  = Bool()
  val fwdData  = UInt(p(XLen).W)
  val fwdMask  = UInt((p(XLen) / 8).W)
}

class StoreForwardPort(implicit p: Parameters) extends Bundle {
  val req  = Input(new StoreForwardReq)
  val resp = Output(new StoreForwardResp)
}

class StoreBufferEntry(implicit p: Parameters) extends Bundle {
  val valid     = Bool()
  val committed = Bool()
  val addrValid = Bool()

  val fwdValid = Bool()

  val seq       = UInt(64.W)
  val rob_tag   = UInt(log2Ceil(p(ROBSize)).W)
  val addr      = UInt(p(XLen).W)
  val data      = UInt(p(XLen).W)
  val mask      = UInt((p(XLen) / 8).W)
  val cacheable = Bool()
}

class StoreBuffer(numLoadPorts: Int, numStorePorts: Int)(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_store_buffer"

  private val IdxW  = log2Ceil(p(StoreBufferSize))
  private val CntW  = log2Ceil(p(StoreBufferSize) + 1)
  private val Bytes = p(XLen) / 8

  val io = IO(new Bundle {
    val alloc  = Flipped(Vec(p(IssueWidth), Valid(new StoreAllocBundle)))
    val commit = Flipped(Vec(p(IssueWidth), Valid(UInt(IdxW.W))))

    val write = Flipped(Vec(numStorePorts, Valid(new StoreWriteBundle)))
    val fwd   = Vec(numLoadPorts, new StoreForwardPort)

    val tail      = Output(UInt(IdxW.W))
    val tailSeq   = Output(UInt(64.W))
    val freeCount = Output(UInt(CntW.W))
    val empty     = Output(Bool())
    val busy      = Output(Bool())

    val mem  = new CacheIO(UInt(p(XLen).W), p(XLen))
    val mmio = new CacheIO(UInt(p(XLen).W), p(XLen))

    val flush = Input(Bool())
  })

  private def wrapAdd(x: UInt, y: UInt): UInt = {
    val sum = x +& y
    Mux(sum >= p(StoreBufferSize).U, sum - p(StoreBufferSize).U, sum)(IdxW - 1, 0)
  }

  val entries = RegInit(VecInit(Seq.fill(p(StoreBufferSize))(0.U.asTypeOf(new StoreBufferEntry))))

  val head  = RegInit(0.U(IdxW.W))
  val tail  = RegInit(0.U(IdxW.W))
  val count = RegInit(0.U(CntW.W))

  // Monotonic sequence
  val tailSeq = RegInit(0.U(64.W))

  val drainOutstanding = RegInit(false.B)
  val drainIsCacheable = RegInit(false.B)

  io.tail      := tail
  io.tailSeq   := tailSeq
  io.freeCount := p(StoreBufferSize).U - count
  io.empty     := count === 0.U && !drainOutstanding
  io.busy      := count =/= 0.U || drainOutstanding

  // Forwarding CAM.
  for (q <- 0 until numLoadPorts) {
    val req = io.fwd(q).req

    val dataStage = Wire(Vec(p(StoreBufferSize) + 1, Vec(Bytes, UInt(8.W))))
    val maskStage = Wire(Vec(p(StoreBufferSize) + 1, Vec(Bytes, Bool())))
    val seqStage  = Wire(Vec(p(StoreBufferSize) + 1, Vec(Bytes, UInt(64.W))))

    val blockStage     = Wire(Vec(p(StoreBufferSize) + 1, Bool()))
    val liveOlderStage = Wire(Vec(p(StoreBufferSize) + 1, Bool()))

    blockStage(0)     := false.B
    liveOlderStage(0) := false.B

    for (b <- 0 until Bytes) {
      dataStage(0)(b) := 0.U
      maskStage(0)(b) := false.B
      seqStage(0)(b)  := 0.U
    }

    for (i <- 0 until p(StoreBufferSize)) {
      val e = entries(i)

      val olderLive =
        req.valid &&
          e.valid &&
          e.seq < req.sq_seq

      val olderFwd =
        req.valid &&
          e.fwdValid &&
          e.seq < req.sq_seq

      val liveUnknown =
        olderLive && !e.addrValid

      val forwardable =
        (olderLive && e.addrValid) || olderFwd

      val sameLine =
        forwardable && e.addr === req.addr

      blockStage(i + 1) :=
        blockStage(i) || liveUnknown

      liveOlderStage(i + 1) :=
        liveOlderStage(i) || olderLive

      for (b <- 0 until Bytes) {
        val byteHit =
          sameLine &&
            e.mask(b) &&
            req.mask(b) &&
            (!maskStage(i)(b) || e.seq > seqStage(i)(b))

        dataStage(i + 1)(b) := Mux(byteHit, e.data(8 * b + 7, 8 * b), dataStage(i)(b))
        maskStage(i + 1)(b) := Mux(byteHit, true.B, maskStage(i)(b))
        seqStage(i + 1)(b)  := Mux(byteHit, e.seq, seqStage(i)(b))
      }
    }

    val finalMaskVec  = maskStage(p(StoreBufferSize))
    val finalDataVec  = dataStage(p(StoreBufferSize))
    val finalMaskUInt = finalMaskVec.asUInt

    io.fwd(q).resp.block    := blockStage(p(StoreBufferSize))
    io.fwd(q).resp.hasOlder := liveOlderStage(p(StoreBufferSize))
    io.fwd(q).resp.fwdValid := (finalMaskUInt & req.mask).orR
    io.fwd(q).resp.fwdFull  := ((finalMaskUInt & req.mask) === req.mask)
    io.fwd(q).resp.fwdData  := Cat((Bytes - 1 to 0 by -1).map(i => finalDataVec(i)))
    io.fwd(q).resp.fwdMask  := finalMaskUInt
  }

  // Drain committed stores in order.
  val headEntry = entries(head)

  val canDrain =
    headEntry.valid &&
      headEntry.committed &&
      headEntry.addrValid &&
      !drainOutstanding

  io.mem.req.valid     := canDrain && headEntry.cacheable
  io.mem.req.bits.op   := CacheOp.WRITE
  io.mem.req.bits.addr := headEntry.addr
  io.mem.req.bits.data := headEntry.data
  io.mem.req.bits.strb := headEntry.mask

  io.mmio.req.valid     := canDrain && !headEntry.cacheable
  io.mmio.req.bits.op   := CacheOp.WRITE
  io.mmio.req.bits.addr := headEntry.addr
  io.mmio.req.bits.data := headEntry.data
  io.mmio.req.bits.strb := headEntry.mask

  io.mem.resp.ready  := drainOutstanding && drainIsCacheable
  io.mmio.resp.ready := drainOutstanding && !drainIsCacheable

  val drainReqFire  = io.mem.req.fire || io.mmio.req.fire
  val drainRespFire = io.mem.resp.fire || io.mmio.resp.fire

  // drain response
  val afterDrainEntries = Wire(Vec(p(StoreBufferSize), new StoreBufferEntry))
  afterDrainEntries := entries

  val afterDrainHead = WireDefault(head)

  when(drainRespFire) {
    afterDrainHead := wrapAdd(head, 1.U)
  }

  for (i <- 0 until p(StoreBufferSize))
    when(drainRespFire && head === i.U) {
      afterDrainEntries(i).valid     := false.B
      afterDrainEntries(i).committed := false.B

      afterDrainEntries(i).addrValid := headEntry.cacheable
      afterDrainEntries(i).fwdValid  := headEntry.cacheable

      when(!headEntry.cacheable) {
        afterDrainEntries(i).seq       := 0.U
        afterDrainEntries(i).rob_tag   := 0.U
        afterDrainEntries(i).addr      := 0.U
        afterDrainEntries(i).data      := 0.U
        afterDrainEntries(i).mask      := 0.U
        afterDrainEntries(i).cacheable := false.B
      }
    }

  // StoreFU writes addr/data
  val afterWriteEntries = Wire(Vec(p(StoreBufferSize), new StoreBufferEntry))
  afterWriteEntries := afterDrainEntries

  for (i <- 0 until p(StoreBufferSize))
    for (s <- 0 until numStorePorts) {
      val hit =
        io.write(s).valid &&
          io.write(s).bits.sq_idx === i.U &&
          afterDrainEntries(i).valid

      when(hit) {
        afterWriteEntries(i).addrValid := true.B
        afterWriteEntries(i).rob_tag   := io.write(s).bits.rob_tag
        afterWriteEntries(i).addr      := io.write(s).bits.addr
        afterWriteEntries(i).data      := io.write(s).bits.data
        afterWriteEntries(i).mask      := io.write(s).bits.mask
        afterWriteEntries(i).cacheable := io.write(s).bits.cacheable
      }
    }

  // ROB commit
  val afterCommitEntries = Wire(Vec(p(StoreBufferSize), new StoreBufferEntry))
  afterCommitEntries := afterWriteEntries

  for (i <- 0 until p(StoreBufferSize))
    for (c <- 0 until p(IssueWidth)) {
      val hit =
        io.commit(c).valid &&
          io.commit(c).bits === i.U &&
          afterWriteEntries(i).valid

      when(hit) {
        afterCommitEntries(i).committed := true.B
      }
    }

  // allocation
  val allocValid = Wire(Vec(p(IssueWidth), Bool()))
  for (a <- 0 until p(IssueWidth))
    allocValid(a) := io.alloc(a).valid && !io.flush

  val allocCount = PopCount(allocValid)

  val afterAllocEntries = Wire(Vec(p(StoreBufferSize), new StoreBufferEntry))
  afterAllocEntries := afterCommitEntries

  for (i <- 0 until p(StoreBufferSize))
    for (a <- 0 until p(IssueWidth)) {
      val hit =
        allocValid(a) &&
          io.alloc(a).bits.sq_idx === i.U

      when(hit) {
        afterAllocEntries(i).valid     := true.B
        afterAllocEntries(i).committed := false.B
        afterAllocEntries(i).addrValid := false.B
        afterAllocEntries(i).fwdValid  := false.B

        afterAllocEntries(i).seq       := io.alloc(a).bits.sq_seq
        afterAllocEntries(i).rob_tag   := io.alloc(a).bits.rob_tag
        afterAllocEntries(i).addr      := 0.U
        afterAllocEntries(i).data      := 0.U
        afterAllocEntries(i).mask      := 0.U
        afterAllocEntries(i).cacheable := false.B
      }
    }

  val afterAllocTail  = wrapAdd(tail, allocCount)
  val afterAllocCount = count + allocCount - drainRespFire.asUInt
  val afterAllocSeq   = tailSeq + allocCount

  // flush
  val keepLive = Wire(Vec(p(StoreBufferSize), Bool()))
  for (i <- 0 until p(StoreBufferSize))
    keepLive(i) := afterAllocEntries(i).valid && afterAllocEntries(i).committed

  val finalEntries = Wire(Vec(p(StoreBufferSize), new StoreBufferEntry))
  finalEntries := afterAllocEntries

  for (i <- 0 until p(StoreBufferSize))
    when(io.flush && afterAllocEntries(i).valid && !afterAllocEntries(i).committed) {
      finalEntries(i).valid     := false.B
      finalEntries(i).committed := false.B
      finalEntries(i).addrValid := false.B
    }

  val finalHead  = afterDrainHead
  val finalTail  = Mux(io.flush, wrapAdd(afterDrainHead, PopCount(keepLive)), afterAllocTail)
  val finalCount = Mux(io.flush, PopCount(keepLive), afterAllocCount)

  val finalTailSeq = afterAllocSeq

  val finalDrainOutstanding = WireDefault(drainOutstanding)
  val finalDrainIsCacheable = WireDefault(drainIsCacheable)

  when(drainReqFire) {
    finalDrainOutstanding := true.B
    finalDrainIsCacheable := headEntry.cacheable
  }

  when(drainRespFire) {
    finalDrainOutstanding := false.B
  }

  entries          := finalEntries
  head             := finalHead
  tail             := finalTail
  count            := finalCount
  tailSeq          := finalTailSeq
  drainOutstanding := finalDrainOutstanding
  drainIsCacheable := finalDrainIsCacheable
}
