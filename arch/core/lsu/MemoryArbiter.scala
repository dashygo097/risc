package arch.core.lsu

import arch.configs._
import arch.configs.proto.FunctionalUnitType._
import vopts.mem.cache._
import chisel3._
import chisel3.util.{ RRArbiter, Queue, log2Ceil }

class MemoryArbiter(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_memory_arbiter"

  private def numLSUs =
    p(FunctionalUnits).zipWithIndex
      .filter(_._1.`type` == FUNCTIONAL_UNIT_TYPE_LSU)
      .size

  val lsu_mem  = IO(Vec(numLSUs, Flipped(new CacheIO(UInt(p(XLen).W), p(XLen)))))
  val lsu_mmio = IO(Vec(numLSUs, Flipped(new CacheIO(UInt(p(XLen).W), p(XLen)))))
  val mem      = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))
  val mmio     = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))

  // Memory request arbiter
  val memReqArb = Module(new RRArbiter(chiselTypeOf(lsu_mem(0).req.bits), numLSUs))
  for (i <- 0 until numLSUs) memReqArb.io.in(i) <> lsu_mem(i).req

  val memRespQueue = Module(new Queue(UInt(log2Ceil(numLSUs).W), p(ROBSize)))
  memReqArb.io.out.ready := mem.req.ready && memRespQueue.io.enq.ready
  mem.req.valid          := memReqArb.io.out.valid && memRespQueue.io.enq.ready
  mem.req.bits           := memReqArb.io.out.bits

  memRespQueue.io.enq.valid := memReqArb.io.out.valid && mem.req.ready
  memRespQueue.io.enq.bits  := memReqArb.io.chosen

  // Memory response demux
  val memTarget = memRespQueue.io.deq.bits
  for (i <- 0 until numLSUs) {
    lsu_mem(i).resp.valid := mem.resp.valid && memRespQueue.io.deq.valid && (memTarget === i.U)
    lsu_mem(i).resp.bits  := mem.resp.bits
  }

  mem.resp.ready            := false.B
  memRespQueue.io.deq.ready := false.B
  when(memRespQueue.io.deq.valid) {
    for (i <- 0 until numLSUs)
      when(memTarget === i.U) {
        val ready = lsu_mem(i).resp.ready
        mem.resp.ready            := ready
        memRespQueue.io.deq.ready := mem.resp.valid && ready
      }
  }

  // MMIO arbiter
  val mmioReqArb = Module(new RRArbiter(chiselTypeOf(lsu_mmio(0).req.bits), numLSUs))
  for (i <- 0 until numLSUs) mmioReqArb.io.in(i) <> lsu_mmio(i).req

  val mmioRespQueue = Module(new Queue(UInt(log2Ceil(numLSUs).W), p(ROBSize)))
  mmioReqArb.io.out.ready := mmio.req.ready && mmioRespQueue.io.enq.ready
  mmio.req.valid          := mmioReqArb.io.out.valid && mmioRespQueue.io.enq.ready
  mmio.req.bits           := mmioReqArb.io.out.bits

  mmioRespQueue.io.enq.valid := mmioReqArb.io.out.valid && mmio.req.ready
  mmioRespQueue.io.enq.bits  := mmioReqArb.io.chosen

  val mmioTarget = mmioRespQueue.io.deq.bits
  for (i <- 0 until numLSUs) {
    lsu_mmio(i).resp.valid := mmio.resp.valid && mmioRespQueue.io.deq.valid && (mmioTarget === i.U)
    lsu_mmio(i).resp.bits  := mmio.resp.bits
  }

  mmio.resp.ready            := false.B
  mmioRespQueue.io.deq.ready := false.B
  when(mmioRespQueue.io.deq.valid) {
    for (i <- 0 until numLSUs)
      when(mmioTarget === i.U) {
        val ready = lsu_mmio(i).resp.ready
        mmio.resp.ready            := ready
        mmioRespQueue.io.deq.ready := mmio.resp.valid && ready
      }
  }
}
