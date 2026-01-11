package arch.system.bridge

import arch.configs._
import vopts.com.amba._
import vopts.mem.cache._
import chisel3._
import chisel3.util._

object AXI4BridgeUtilities extends RegisteredUtilities[BusBridgeUtilities] {
  override def utils: BusBridgeUtilities = new BusBridgeUtilities {
    override def name: String = "axi"

    override def busType: Bundle =
      new AXILiteMasterIO(addrWidth = p(XLen), dataWidth = p(XLen))

    override def createBridge(memory: UnifiedMemoryIO): Bundle = {
      val axi4 = Wire(new AXILiteMasterIO(addrWidth = p(XLen), dataWidth = p(XLen)))

      axi4.aw.valid     := memory.req.valid && (memory.req.bits.op === MemoryOp.WRITE)
      axi4.aw.bits.addr := memory.req.bits.addr
      axi4.aw.bits.prot := 0.U

      axi4.w.valid     := memory.req.valid && (memory.req.bits.op === MemoryOp.WRITE)
      axi4.w.bits.data := memory.req.bits.data
      axi4.w.bits.strb := Fill(p(XLen) / 8, 1.U) // TODO: flatten strb for axi4 bus

      axi4.b.ready := true.B

      axi4.ar.valid     := memory.req.valid && (memory.req.bits.op === MemoryOp.READ)
      axi4.ar.bits.addr := memory.req.bits.addr
      axi4.ar.bits.prot := 0.U

      axi4.r.ready := memory.resp.ready

      memory.req.ready := MuxCase(
        false.B,
        Seq(
          (memory.req.bits.op === MemoryOp.WRITE) -> (axi4.aw.ready && axi4.w.ready),
          (memory.req.bits.op === MemoryOp.READ)  -> axi4.ar.ready
        )
      )

      memory.resp.valid     := MuxCase(
        false.B,
        Seq(
          (memory.req.bits.op === MemoryOp.WRITE) -> axi4.b.valid,
          (memory.req.bits.op === MemoryOp.READ)  -> axi4.r.valid
        )
      )
      memory.resp.bits.data := axi4.r.bits.data

      axi4
    }
  }

  override def factory: UtilitiesFactory[BusBridgeUtilities] = BusBridgeUtilitiesFactory
}
