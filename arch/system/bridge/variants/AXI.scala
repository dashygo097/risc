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

      axi.aw.valid     := memory.req.valid && (memory.req.bits.op === MemoryOp.WRITE)
      axi.aw.bits.addr := memory.req.bits.addr
      axi.aw.bits.prot := 0.U

      axi.w.valid     := memory.req.valid && (memory.req.bits.op === MemoryOp.WRITE)
      axi.w.bits.data := memory.req.bits.data
      axi.w.bits.strb := Fill(p(XLen) / 8, 1.U) // TODO: flatten strb for axi bus

      axi.b.ready := true.B

      axi.ar.valid     := memory.req.valid && (memory.req.bits.op === MemoryOp.READ)
      axi.ar.bits.addr := memory.req.bits.addr
      axi.ar.bits.prot := 0.U

      axi.r.ready := memory.resp.ready

      memory.req.ready := MuxCase(
        false.B,
        Seq(
          (memory.req.bits.op === MemoryOp.WRITE) -> (axi.aw.ready && axi.w.ready),
          (memory.req.bits.op === MemoryOp.READ)  -> axi.ar.ready
        )
      )

      memory.resp.valid     := MuxCase(
        false.B,
        Seq(
          (memory.req.bits.op === MemoryOp.WRITE) -> axi.b.valid,
          (memory.req.bits.op === MemoryOp.READ)  -> axi.r.valid
        )
      )
      memory.resp.bits.data := axi.r.bits.data

      axi
    }
  }

  override def factory: UtilitiesFactory[BusBridgeUtilities] = BusBridgeUtilitiesFactory
}
