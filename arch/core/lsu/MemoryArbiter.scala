package arch.core.lsu

import arch.configs._
import arch.configs.proto.FunctionalUnitType._
import vopts.mem.cache._
import chisel3._
import chisel3.util._

class MemoryArbiter(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_memory_arbiter"

  private val NumLDs  = p(FunctionalUnits).count(_.`type` == FUNCTIONAL_UNIT_TYPE_LD)
  private val NumReqs = NumLDs + 1
  private val TargetW = log2Ceil(NumReqs)

  val ld_mem  = IO(Vec(NumLDs, Flipped(new CacheIO(UInt(p(XLen).W), p(XLen)))))
  val ld_mmio = IO(Vec(NumLDs, Flipped(new CacheIO(UInt(p(XLen).W), p(XLen)))))

  val store_mem  = IO(Flipped(new CacheIO(UInt(p(XLen).W), p(XLen))))
  val store_mmio = IO(Flipped(new CacheIO(UInt(p(XLen).W), p(XLen))))

  val mem  = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))
  val mmio = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))

  val memArb  = Module(new RRArbiter(new CacheReq(UInt(p(XLen).W), p(XLen)), NumReqs))
  val mmioArb = Module(new RRArbiter(new CacheReq(UInt(p(XLen).W), p(XLen)), NumReqs))

  val memRespQ  = Module(new Queue(UInt(TargetW.W), p(ROBSize), pipe = true, flow = true))
  val mmioRespQ = Module(new Queue(UInt(TargetW.W), p(ROBSize), pipe = true, flow = true))

  for (i <- 0 until NumLDs) memArb.io.in(i) <> ld_mem(i).req
  memArb.io.in(NumLDs) <> store_mem.req

  mem.req.valid       := memArb.io.out.valid && memRespQ.io.enq.ready
  mem.req.bits        := memArb.io.out.bits
  memArb.io.out.ready := mem.req.ready && memRespQ.io.enq.ready

  memRespQ.io.enq.valid := memArb.io.out.fire
  memRespQ.io.enq.bits  := memArb.io.chosen

  val memTarget    = memRespQ.io.deq.bits
  val memRespValid = mem.resp.valid && memRespQ.io.deq.valid

  for (i <- 0 until NumLDs) {
    ld_mem(i).resp.valid := memRespValid && memTarget === i.U
    ld_mem(i).resp.bits  := mem.resp.bits
  }

  store_mem.resp.valid := memRespValid && memTarget === NumLDs.U
  store_mem.resp.bits  := mem.resp.bits

  val memLdReadyVec = Wire(Vec(NumLDs, Bool()))
  for (i <- 0 until NumLDs) memLdReadyVec(i) := ld_mem(i).resp.ready

  val memTargetReady = Mux(memTarget === NumLDs.U, store_mem.resp.ready, Mux1H((0 until NumLDs).map(i => (memTarget === i.U) -> memLdReadyVec(i))))
  mem.resp.ready        := memRespQ.io.deq.valid && memTargetReady
  memRespQ.io.deq.ready := mem.resp.valid && memTargetReady

  for (i <- 0 until NumLDs) mmioArb.io.in(i) <> ld_mmio(i).req
  mmioArb.io.in(NumLDs) <> store_mmio.req

  mmio.req.valid       := mmioArb.io.out.valid && mmioRespQ.io.enq.ready
  mmio.req.bits        := mmioArb.io.out.bits
  mmioArb.io.out.ready := mmio.req.ready && mmioRespQ.io.enq.ready

  mmioRespQ.io.enq.valid := mmioArb.io.out.fire
  mmioRespQ.io.enq.bits  := mmioArb.io.chosen

  val mmioTarget    = mmioRespQ.io.deq.bits
  val mmioRespValid = mmio.resp.valid && mmioRespQ.io.deq.valid

  for (i <- 0 until NumLDs) {
    ld_mmio(i).resp.valid := mmioRespValid && mmioTarget === i.U
    ld_mmio(i).resp.bits  := mmio.resp.bits
  }

  store_mmio.resp.valid := mmioRespValid && mmioTarget === NumLDs.U
  store_mmio.resp.bits  := mmio.resp.bits

  val mmioLdReadyVec = Wire(Vec(NumLDs, Bool()))
  for (i <- 0 until NumLDs) mmioLdReadyVec(i) := ld_mmio(i).resp.ready

  val mmioTargetReady = Mux(mmioTarget === NumLDs.U, store_mmio.resp.ready, Mux1H((0 until NumLDs).map(i => (mmioTarget === i.U) -> mmioLdReadyVec(i))))
  mmio.resp.ready        := mmioRespQ.io.deq.valid && mmioTargetReady
  mmioRespQ.io.deq.ready := mmio.resp.valid && mmioTargetReady
}
