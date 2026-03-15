package arch.core.csr

import arch.configs._
import vopts.utils._
import chisel3._

class CsrFile(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_csrfile"

  val utils = CsrUtilitiesFactory.getOrThrow(p(ISA))

  val en   = IO(Input(Bool()))
  val cmd  = IO(Input(UInt(utils.cmdWidth.W)))
  val imm  = IO(Input(UInt(utils.immWidth.W)))
  val addr = IO(Input(UInt(utils.addrWidth.W)))
  val src  = IO(Input(UInt(p(XLen).W)))
  val rd   = IO(Output(UInt(p(XLen).W)))

  val extraInputIO: Map[String, UInt] = utils.extraInputs.map { case (name, width) =>
    val port = IO(Input(UInt(width.W)))
    port.suggestName(s"extra_$name")
    name -> port
  }.toMap

  val csrTable: Seq[Register] = utils.table.map(_._1)
  val addrMap: Seq[UInt]      = csrTable.map(_.addr.U(utils.addrWidth.W))

  val csrRegs: Seq[UInt] = csrTable.zipWithIndex.map { case (reg, _) =>
    val r = RegInit(reg.initValue.U(p(XLen).W))
    r.suggestName(reg.name)
    r
  }

  val hits: Seq[Bool]          = addrMap.map(_ === addr)
  val addrMatch: Bool          = CombTree.orTree(hits)
  val writableHits: Seq[Bool]  = csrTable.zip(hits).map { case (reg, h) =>
    h && reg.writable.B
  }
  val writeAccessAllowed: Bool = addrMatch && CombTree.orTree(writableHits)
  val srcData: UInt            = Mux(utils.isImm(cmd), utils.genImm(imm), src)

  utils.table.zipWithIndex.foreach { case ((reg, behavior), i) =>
    behavior match {

      case AlwaysUpdate(fn) =>
        csrRegs(i) := fn(extraInputIO)

      case ConditionalUpdate(fn) =>
        csrRegs(i) := fn(extraInputIO)
        when(en && writeAccessAllowed && hits(i) && reg.writable.B) {
          csrRegs(i) := utils.fn(cmd, csrRegs(i), srcData)
        }

      case NormalUpdate =>
        when(en && writeAccessAllowed && hits(i) && reg.writable.B) {
          csrRegs(i) := utils.fn(cmd, csrRegs(i), srcData)
        }
    }
  }

  val readCases: Seq[(Bool, UInt)] = hits.zip(csrRegs)

  rd := Mux(
    en,
    CombTree.oneHotMux(readCases),
    0.U(p(XLen).W)
  )
}
