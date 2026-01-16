package arch.system.bridge

import arch.configs._
import vopts.com.amba._
import vopts.mem.cache._
import chisel3._
import chisel3.util._

object AXIBridgeUtilities extends RegisteredUtilities[BusBridgeUtilities] {
  override def utils: BusBridgeUtilities = new BusBridgeUtilities {
    override def name: String = "axi"

    override def busType: Bundle =
      new AXILiteMasterIO(addrWidth = p(XLen), dataWidth = p(XLen))

    override def createBridge(memory: UnifiedMemoryIO): Bundle = {
      val axi = Wire(new AXILiteMasterIO(addrWidth = p(XLen), dataWidth = p(XLen)))

      val isWrite = memory.req.bits.op === MemoryOp.WRITE
      val isRead  = memory.req.bits.op === MemoryOp.READ

      // AW
      axi.aw.valid     := memory.req.valid && isWrite
      axi.aw.bits.addr := memory.req.bits.addr
      axi.aw.bits.prot := 0.U

      // W
      axi.w.valid     := memory.req.valid && isWrite
      axi.w.bits.data := memory.req.bits.data
      axi.w.bits.strb := Fill(p(XLen) / 8, 1.U) // TODO:

      // B
      axi.b.ready := memory.resp.ready

      // AR
      axi.ar.valid     := memory.req.valid && isRead
      axi.ar.bits.addr := memory.req.bits.addr
      axi.ar.bits.prot := 0.U

      // R
      axi.r.ready := memory.resp.ready

      val writeAccepted = memory.req.valid && isWrite && axi.aw.ready && axi.w.ready
      val readAccepted  = memory.req.valid && isRead && axi.ar.ready

      memory.req.ready := writeAccepted || readAccepted

      memory.resp.valid     := Mux(isWrite, axi.b.valid, axi.r.valid)
      memory.resp.bits.data := axi.r.bits.data

      axi
    }

    override def createBridgeReadOnly(memory: UnifiedMemoryReadOnlyIO): Bundle = {
      val axi = Wire(new AXILiteMasterIO(addrWidth = p(XLen), dataWidth = p(XLen)))

      // AW
      axi.aw.valid     := false.B
      axi.aw.bits.addr := 0.U(p(XLen).W)
      axi.aw.bits.prot := 0.U(3.W)

      // W
      axi.w.valid     := false.B
      axi.w.bits.data := 0.U(p(XLen).W)
      axi.w.bits.strb := 0.U((p(XLen) / 8).W)

      // B
      axi.b.ready := false.B

      // AR
      axi.ar.valid     := memory.req.valid
      axi.ar.bits.addr := memory.req.bits.addr
      axi.ar.bits.prot := 0.U(3.W)

      // R
      axi.r.ready := memory.resp.ready

      val readAccepted = memory.req.valid && axi.ar.ready

      memory.req.ready := readAccepted

      memory.resp.valid     := axi.r.valid
      memory.resp.bits.data := axi.r.bits.data

      axi
    }
  }

  override def factory: UtilitiesFactory[BusBridgeUtilities] = BusBridgeUtilitiesFactory
}
