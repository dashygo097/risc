package arch.core.lsu

import arch.configs._
import arch.configs.proto.FunctionalUnitType._
import vopts.mem.cache._
import chisel3._
import chisel3.util.{ log2Ceil, RRArbiter, Queue, UIntToOH }

class MemoryArbiterRoutedReq(targetWidth: Int)(implicit p: Parameters) extends Bundle {
  val target = UInt(targetWidth.W)
  val req    = new CacheReq(UInt(p(XLen).W), p(XLen))
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

  val memRespQ  = Module(new Queue(UInt(TargetW.W), p(ROBSize), pipe = true, flow = true))
  val mmioRespQ = Module(new Queue(UInt(TargetW.W), p(ROBSize), pipe = true, flow = true))

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

  val memTarget    = memRespQ.io.deq.bits
  val memRespValid = mem.resp.valid && memRespQ.io.deq.valid

  val memRespReadyVec = Wire(Vec(NumReqs, Bool()))

  for (i <- 0 until NumLDs) {
    ld_mem(i).resp.valid := memRespValid && memTarget === i.U
    ld_mem(i).resp.bits  := mem.resp.bits
    memRespReadyVec(i)   := ld_mem(i).resp.ready
  }

  store_mem.resp.valid    := memRespValid && memTarget === NumLDs.U
  store_mem.resp.bits     := mem.resp.bits
  memRespReadyVec(NumLDs) := store_mem.resp.ready

  val memTargetReady = (memRespReadyVec.asUInt & UIntToOH(memTarget, NumReqs)).orR

  mem.resp.ready        := memRespQ.io.deq.valid && memTargetReady
  memRespQ.io.deq.ready := mem.resp.valid && memTargetReady

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

  val mmioTarget    = mmioRespQ.io.deq.bits
  val mmioRespValid = mmio.resp.valid && mmioRespQ.io.deq.valid

  val mmioRespReadyVec = Wire(Vec(NumReqs, Bool()))

  for (i <- 0 until NumLDs) {
    ld_mmio(i).resp.valid := mmioRespValid && mmioTarget === i.U
    ld_mmio(i).resp.bits  := mmio.resp.bits
    mmioRespReadyVec(i)   := ld_mmio(i).resp.ready
  }

  store_mmio.resp.valid    := mmioRespValid && mmioTarget === NumLDs.U
  store_mmio.resp.bits     := mmio.resp.bits
  mmioRespReadyVec(NumLDs) := store_mmio.resp.ready

  val mmioTargetReady = (mmioRespReadyVec.asUInt & UIntToOH(mmioTarget, NumReqs)).orR

  mmio.resp.ready        := mmioRespQ.io.deq.valid && mmioTargetReady
  mmioRespQ.io.deq.ready := mmio.resp.valid && mmioTargetReady
}
