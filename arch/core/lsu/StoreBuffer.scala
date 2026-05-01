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
  val mask      = UInt(p(BytesPerWord).W)
  val cacheable = Bool()
}

class StoreForwardReq(implicit p: Parameters) extends Bundle {
  val valid  = Bool()
  val sq_seq = UInt(64.W)
  val addr   = UInt(p(XLen).W)
  val mask   = UInt(p(BytesPerWord).W)
}

class StoreForwardResp(implicit p: Parameters) extends Bundle {
  val block    = Bool()
  val hasOlder = Bool()
  val fwdValid = Bool()
  val fwdFull  = Bool()
  val fwdData  = UInt(p(XLen).W)
  val fwdMask  = UInt(p(BytesPerWord).W)
}

class StoreForwardPort(implicit p: Parameters) extends Bundle {
  val req  = Flipped(Decoupled(new StoreForwardReq))
  val resp = Decoupled(new StoreForwardResp)
}

class StoreBufferEntry(implicit p: Parameters) extends Bundle {
  val valid     = Bool()
  val committed = Bool()
  val addrValid = Bool()
  val fwdValid  = Bool()
  val seq       = UInt(64.W)
  val rob_tag   = UInt(log2Ceil(p(ROBSize)).W)
  val addr      = UInt(p(XLen).W)
  val data      = UInt(p(XLen).W)
  val mask      = UInt(p(BytesPerWord).W)
  val cacheable = Bool()
}

class StoreBuffer(numLoadPorts: Int, numStorePorts: Int)(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_store_buffer"

  private val IdxW = log2Ceil(p(StoreBufferSize))
  private val CntW = log2Ceil(p(StoreBufferSize) + 1)

  val io = IO(new Bundle {
    val alloc     = Flipped(Vec(p(IssueWidth), Valid(new StoreAllocBundle)))
    val commit    = Flipped(Vec(p(IssueWidth), Valid(UInt(IdxW.W))))
    val write     = Flipped(Vec(numStorePorts, Valid(new StoreWriteBundle)))
    val fwd       = Vec(numLoadPorts, new StoreForwardPort)
    val tail      = Output(UInt(IdxW.W))
    val tailSeq   = Output(UInt(64.W))
    val freeCount = Output(UInt(CntW.W))
    val empty     = Output(Bool())
    val busy      = Output(Bool())
    val mem       = new CacheIO(UInt(p(XLen).W), p(XLen))
    val mmio      = new CacheIO(UInt(p(XLen).W), p(XLen))
    val flush     = Input(Bool())
  })

  private def wrapAdd(x: UInt, y: UInt): UInt = {
    val sum = x +& y
    Mux(sum >= p(StoreBufferSize).U, sum - p(StoreBufferSize).U, sum)(IdxW - 1, 0)
  }

  private def zeroEntry: StoreBufferEntry = 0.U.asTypeOf(new StoreBufferEntry)

  val entries          = RegInit(VecInit(Seq.fill(p(StoreBufferSize))(zeroEntry)))
  val head             = RegInit(0.U(IdxW.W))
  val tail             = RegInit(0.U(IdxW.W))
  val count            = RegInit(0.U(CntW.W))
  val tailSeq          = RegInit(0.U(64.W))
  val drainOutstanding = RegInit(false.B)
  val drainIsCacheable = RegInit(false.B)

  io.tail      := tail
  io.tailSeq   := tailSeq
  io.freeCount := p(StoreBufferSize).U(CntW.W) - count
  io.empty     := count === 0.U && !drainOutstanding
  io.busy      := count =/= 0.U || drainOutstanding

  for (q <- 0 until numLoadPorts) {
    val fwdRespValid = RegInit(false.B)
    val fwdRespBits  = RegInit(0.U.asTypeOf(new StoreForwardResp))

    io.fwd(q).req.ready  := (!fwdRespValid || io.fwd(q).resp.ready) && !io.flush
    io.fwd(q).resp.valid := fwdRespValid && !io.flush
    io.fwd(q).resp.bits  := fwdRespBits

    val req       = io.fwd(q).req.bits
    val reqFire   = io.fwd(q).req.fire
    val dataStage = Wire(Vec(p(StoreBufferSize) + 1, Vec(p(BytesPerWord), UInt(8.W))))
    val maskStage = Wire(Vec(p(StoreBufferSize) + 1, Vec(p(BytesPerWord), Bool())))
    val olderVec  = Wire(Vec(p(StoreBufferSize), Bool()))
    val blockVec  = Wire(Vec(p(StoreBufferSize), Bool()))

    for (b <- 0 until p(BytesPerWord)) {
      dataStage(0)(b) := 0.U
      maskStage(0)(b) := false.B
    }

    for (logical <- 0 until p(StoreBufferSize)) {
      val idx         = wrapAdd(head, logical.U)
      val e           = entries(idx)
      val inRange     = logical.U < count
      val olderLive   = reqFire && req.valid && inRange && e.valid && e.seq < req.sq_seq
      val liveUnknown = olderLive && !e.addrValid
      val forwardable = olderLive && e.addrValid
      val sameLine    = forwardable && e.addr === req.addr

      olderVec(logical) := olderLive
      blockVec(logical) := liveUnknown

      for (b <- 0 until p(BytesPerWord)) {
        val byteHit = sameLine && e.mask(b) && req.mask(b)
        dataStage(logical + 1)(b) := Mux(byteHit, e.data(8 * b + 7, 8 * b), dataStage(logical)(b))
        maskStage(logical + 1)(b) := Mux(byteHit, true.B, maskStage(logical)(b))
      }
    }

    val finalMaskVec  = maskStage(p(StoreBufferSize))
    val finalDataVec  = dataStage(p(StoreBufferSize))
    val finalMaskUInt = finalMaskVec.asUInt

    val nextResp = Wire(new StoreForwardResp)
    nextResp.block    := blockVec.asUInt.orR
    nextResp.hasOlder := olderVec.asUInt.orR
    nextResp.fwdValid := reqFire && req.valid && (finalMaskUInt & req.mask).orR
    nextResp.fwdFull  := reqFire && req.valid && ((finalMaskUInt & req.mask) === req.mask)
    nextResp.fwdData  := Cat((p(BytesPerWord) - 1 to 0 by -1).map(i => finalDataVec(i)))
    nextResp.fwdMask  := finalMaskUInt

    when(io.flush) {
      fwdRespValid := false.B
      fwdRespBits  := 0.U.asTypeOf(new StoreForwardResp)
    }.otherwise {
      when(reqFire) {
        fwdRespValid := true.B
        fwdRespBits  := nextResp
      }.elsewhen(io.fwd(q).resp.fire) {
        fwdRespValid := false.B
      }
    }
  }

  val headEntry = entries(head)
  val canDrain  = headEntry.valid && headEntry.committed && headEntry.addrValid && !drainOutstanding

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

  val afterDrainEntries = Wire(Vec(p(StoreBufferSize), new StoreBufferEntry))
  afterDrainEntries := entries

  val afterDrainHead = WireDefault(head)

  when(drainRespFire) {
    afterDrainHead := wrapAdd(head, 1.U)
  }

  for (i <- 0 until p(StoreBufferSize))
    when(drainRespFire && head === i.U) {
      afterDrainEntries(i) := zeroEntry
    }

  val afterWriteEntries = Wire(Vec(p(StoreBufferSize), new StoreBufferEntry))
  afterWriteEntries := afterDrainEntries

  for (i <- 0 until p(StoreBufferSize))
    for (s <- 0 until numStorePorts) {
      val hit = io.write(s).valid && io.write(s).bits.sq_idx === i.U && afterDrainEntries(i).valid && afterDrainEntries(i).rob_tag === io.write(s).bits.rob_tag

      when(hit) {
        afterWriteEntries(i).addrValid := true.B
        afterWriteEntries(i).fwdValid  := true.B
        afterWriteEntries(i).addr      := io.write(s).bits.addr
        afterWriteEntries(i).data      := io.write(s).bits.data
        afterWriteEntries(i).mask      := io.write(s).bits.mask
        afterWriteEntries(i).cacheable := io.write(s).bits.cacheable
      }
    }

  val afterCommitEntries = Wire(Vec(p(StoreBufferSize), new StoreBufferEntry))
  afterCommitEntries := afterWriteEntries

  for (i <- 0 until p(StoreBufferSize))
    for (c <- 0 until p(IssueWidth)) {
      val hit = io.commit(c).valid && io.commit(c).bits === i.U && afterWriteEntries(i).valid

      when(hit) {
        afterCommitEntries(i).committed := true.B
      }
    }

  val allocValid = Wire(Vec(p(IssueWidth), Bool()))
  for (a <- 0 until p(IssueWidth))
    allocValid(a) := io.alloc(a).valid && !io.flush

  val allocCount        = PopCount(allocValid)
  val afterAllocEntries = Wire(Vec(p(StoreBufferSize), new StoreBufferEntry))
  afterAllocEntries := afterCommitEntries

  for (i <- 0 until p(StoreBufferSize))
    for (a <- 0 until p(IssueWidth)) {
      val hit = allocValid(a) && io.alloc(a).bits.sq_idx === i.U

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

  val afterAllocTail      = wrapAdd(tail, allocCount)
  val afterAllocCountWide = count +& allocCount - drainRespFire.asUInt
  val afterAllocCount     = afterAllocCountWide(CntW - 1, 0)
  val afterAllocSeq       = tailSeq + allocCount

  val compactEntries = Wire(Vec(p(StoreBufferSize), new StoreBufferEntry))
  for (i <- 0 until p(StoreBufferSize))
    compactEntries(i) := zeroEntry

  val keepVec = Wire(Vec(p(StoreBufferSize), Bool()))
  for (i <- 0 until p(StoreBufferSize))
    keepVec(i) := false.B

  for (logical <- 0 until p(StoreBufferSize)) {
    val srcIdx   = wrapAdd(afterDrainHead, logical.U)
    val srcEntry = afterAllocEntries(srcIdx)
    val keep     = logical.U < afterAllocCount && srcEntry.valid && srcEntry.committed
    val dstIdx   = PopCount((0 until logical).map(j => keepVec(j)))

    keepVec(logical) := keep

    when(keep) {
      compactEntries(dstIdx) := srcEntry
    }
  }

  val compactCount = PopCount(keepVec)
  val finalEntries = Wire(Vec(p(StoreBufferSize), new StoreBufferEntry))
  finalEntries := afterAllocEntries

  when(io.flush) {
    finalEntries := compactEntries
  }

  val finalHead  = Mux(io.flush, 0.U, afterDrainHead)
  val finalTail  = Mux(io.flush, compactCount(IdxW - 1, 0), afterAllocTail)
  val finalCount = Mux(io.flush, compactCount, afterAllocCount)

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
  tailSeq          := afterAllocSeq
  drainOutstanding := finalDrainOutstanding
  drainIsCacheable := finalDrainIsCacheable
}
