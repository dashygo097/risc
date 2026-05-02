package arch.core.lsu

import arch.configs._
import arch.configs.proto.FunctionalUnitType._
import vopts.mem.cache._
import chisel3._
import chisel3.util.{ log2Ceil, Decoupled, RRArbiter, UIntToOH }

class MemoryArbiterRoutedReq(targetWidth: Int)(implicit p: Parameters) extends Bundle {
  val target = UInt(targetWidth.W)
  val req    = new CacheReq(UInt(p(XLen).W), p(XLen))
}

class MemoryArbiterRoutedResp(targetWidth: Int)(implicit p: Parameters) extends Bundle {
  val target = UInt(targetWidth.W)
  val resp   = new CacheResp(UInt(p(XLen).W))
}

class MemoryArbiterFfQueue[T <: Data](gen: T, depth: Int) extends Module {
  require(depth > 0)

  private val PtrW = math.max(1, log2Ceil(depth))
  private val CntW = log2Ceil(depth + 1)

  val io = IO(new Bundle {
    val enq = Flipped(Decoupled(gen))
    val deq = Decoupled(gen)
  })

  private def wrapInc(x: UInt): UInt =
    if (depth == 1) 0.U else Mux(x === (depth - 1).U, 0.U, x + 1.U)(PtrW - 1, 0)

  val ram   = Reg(Vec(depth, gen))
  val head  = RegInit(0.U(PtrW.W))
  val tail  = RegInit(0.U(PtrW.W))
  val count = RegInit(0.U(CntW.W))

  val empty = count === 0.U
  val full  = count === depth.U

  io.enq.ready := !full
  io.deq.valid := !empty
  io.deq.bits  := ram(head)

  val enqFire = io.enq.fire
  val deqFire = io.deq.fire

  when(enqFire) {
    ram(tail) := io.enq.bits
    tail      := wrapInc(tail)
  }

  when(deqFire) {
    head := wrapInc(head)
  }

  when(enqFire =/= deqFire) {
    count := Mux(enqFire, count + 1.U, count - 1.U)
  }
}

class MemoryArbiter(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_memory_arbiter"

  private val NumLDs  = p(FunctionalUnits).count(_.`type` == FUNCTIONAL_UNIT_TYPE_LD)
  private val NumReqs = NumLDs + 1
  private val TargetW = math.max(1, log2Ceil(NumReqs))

  val ld_mem  = IO(Vec(NumLDs, Flipped(new CacheIO(UInt(p(XLen).W), p(XLen)))))
  val ld_mmio = IO(Vec(NumLDs, Flipped(new CacheIO(UInt(p(XLen).W), p(XLen)))))

  val store_mem  = IO(Flipped(new CacheIO(UInt(p(XLen).W), p(XLen))))
  val store_mmio = IO(Flipped(new CacheIO(UInt(p(XLen).W), p(XLen))))

  val mem  = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))
  val mmio = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))

  val memArb  = Module(new RRArbiter(new MemoryArbiterRoutedReq(TargetW), NumReqs))
  val mmioArb = Module(new RRArbiter(new MemoryArbiterRoutedReq(TargetW), NumReqs))

  val memRespQ   = Module(new MemoryArbiterFfQueue(UInt(TargetW.W), p(ROBSize)))
  val mmioRespQ  = Module(new MemoryArbiterFfQueue(UInt(TargetW.W), p(ROBSize)))
  val memRespBuf = Module(new MemoryArbiterFfQueue(new MemoryArbiterRoutedResp(TargetW), 2))
  val mmioRespBuf = Module(new MemoryArbiterFfQueue(new MemoryArbiterRoutedResp(TargetW), 2))

  val memReqValid = RegInit(false.B)
  val memReqBits  = Reg(new MemoryArbiterRoutedReq(TargetW))

  val mmioReqValid = RegInit(false.B)
  val mmioReqBits  = Reg(new MemoryArbiterRoutedReq(TargetW))

  for (i <- 0 until NumLDs) {
    memArb.io.in(i).valid       := ld_mem(i).req.valid
    memArb.io.in(i).bits.target := i.U(TargetW.W)
    memArb.io.in(i).bits.req    := ld_mem(i).req.bits
    ld_mem(i).req.ready         := memArb.io.in(i).ready
  }

  memArb.io.in(NumLDs).valid       := store_mem.req.valid
  memArb.io.in(NumLDs).bits.target := NumLDs.U(TargetW.W)
  memArb.io.in(NumLDs).bits.req    := store_mem.req.bits
  store_mem.req.ready              := memArb.io.in(NumLDs).ready

  mem.req.valid := memReqValid && memRespQ.io.enq.ready
  mem.req.bits  := memReqBits.req

  memRespQ.io.enq.valid := memReqValid && mem.req.ready
  memRespQ.io.enq.bits  := memReqBits.target

  val memIssueFire = memReqValid && mem.req.ready && memRespQ.io.enq.ready
  val memTakeFire  = memArb.io.out.valid && memArb.io.out.ready

  memArb.io.out.ready := !memReqValid || memIssueFire

  when(memTakeFire) {
    memReqValid := true.B
    memReqBits  := memArb.io.out.bits
  }.elsewhen(memIssueFire) {
    memReqValid := false.B
  }

  memRespBuf.io.enq.valid       := mem.resp.valid && memRespQ.io.deq.valid
  memRespBuf.io.enq.bits.target := memRespQ.io.deq.bits
  memRespBuf.io.enq.bits.resp   := mem.resp.bits

  mem.resp.ready        := memRespQ.io.deq.valid && memRespBuf.io.enq.ready
  memRespQ.io.deq.ready := mem.resp.valid && memRespBuf.io.enq.ready

  val memTarget    = memRespBuf.io.deq.bits.target
  val memRespValid = memRespBuf.io.deq.valid

  val memRespReadyVec = Wire(Vec(NumReqs, Bool()))

  for (i <- 0 until NumLDs) {
    ld_mem(i).resp.valid := memRespValid && memTarget === i.U
    ld_mem(i).resp.bits  := memRespBuf.io.deq.bits.resp
    memRespReadyVec(i)   := ld_mem(i).resp.ready
  }

  store_mem.resp.valid    := memRespValid && memTarget === NumLDs.U
  store_mem.resp.bits     := memRespBuf.io.deq.bits.resp
  memRespReadyVec(NumLDs) := store_mem.resp.ready

  val memTargetReady = (memRespReadyVec.asUInt & UIntToOH(memTarget, NumReqs)).orR

  memRespBuf.io.deq.ready := memTargetReady

  for (i <- 0 until NumLDs) {
    mmioArb.io.in(i).valid       := ld_mmio(i).req.valid
    mmioArb.io.in(i).bits.target := i.U(TargetW.W)
    mmioArb.io.in(i).bits.req    := ld_mmio(i).req.bits
    ld_mmio(i).req.ready         := mmioArb.io.in(i).ready
  }

  mmioArb.io.in(NumLDs).valid       := store_mmio.req.valid
  mmioArb.io.in(NumLDs).bits.target := NumLDs.U(TargetW.W)
  mmioArb.io.in(NumLDs).bits.req    := store_mmio.req.bits
  store_mmio.req.ready              := mmioArb.io.in(NumLDs).ready

  mmio.req.valid := mmioReqValid && mmioRespQ.io.enq.ready
  mmio.req.bits  := mmioReqBits.req

  mmioRespQ.io.enq.valid := mmioReqValid && mmio.req.ready
  mmioRespQ.io.enq.bits  := mmioReqBits.target

  val mmioIssueFire = mmioReqValid && mmio.req.ready && mmioRespQ.io.enq.ready
  val mmioTakeFire  = mmioArb.io.out.valid && mmioArb.io.out.ready

  mmioArb.io.out.ready := !mmioReqValid || mmioIssueFire

  when(mmioTakeFire) {
    mmioReqValid := true.B
    mmioReqBits  := mmioArb.io.out.bits
  }.elsewhen(mmioIssueFire) {
    mmioReqValid := false.B
  }

  mmioRespBuf.io.enq.valid       := mmio.resp.valid && mmioRespQ.io.deq.valid
  mmioRespBuf.io.enq.bits.target := mmioRespQ.io.deq.bits
  mmioRespBuf.io.enq.bits.resp   := mmio.resp.bits

  mmio.resp.ready        := mmioRespQ.io.deq.valid && mmioRespBuf.io.enq.ready
  mmioRespQ.io.deq.ready := mmio.resp.valid && mmioRespBuf.io.enq.ready

  val mmioTarget    = mmioRespBuf.io.deq.bits.target
  val mmioRespValid = mmioRespBuf.io.deq.valid

  val mmioRespReadyVec = Wire(Vec(NumReqs, Bool()))

  for (i <- 0 until NumLDs) {
    ld_mmio(i).resp.valid := mmioRespValid && mmioTarget === i.U
    ld_mmio(i).resp.bits  := mmioRespBuf.io.deq.bits.resp
    mmioRespReadyVec(i)   := ld_mmio(i).resp.ready
  }

  store_mmio.resp.valid    := mmioRespValid && mmioTarget === NumLDs.U
  store_mmio.resp.bits     := mmioRespBuf.io.deq.bits.resp
  mmioRespReadyVec(NumLDs) := store_mmio.resp.ready

  val mmioTargetReady = (mmioRespReadyVec.asUInt & UIntToOH(mmioTarget, NumReqs)).orR

  mmioRespBuf.io.deq.ready := mmioTargetReady
}
