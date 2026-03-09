package arch.core.csr

import arch.configs._
import chisel3._
import chisel3.util._

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

  val csrTable     = utils.table.map(_._1)
  val addr_map     = csrTable.map(_.addr.U)
  val writable_vec = VecInit(csrTable.map(_.writable.B))

  val csrRegs = csrTable.zipWithIndex.map { case (reg, _) =>
    val r = RegInit(reg.initValue.U(p(XLen).W))
    r.suggestName(reg.name)
    r
  }

  val addr_match           = addr_map.map(_ === addr).reduce(_ || _)
  val write_access_allowed = addr_match &&
    MuxCase(
      false.B,
      csrTable.zipWithIndex.map { case (reg, i) =>
        (addr === addr_map(i)) -> writable_vec(i)
      }
    )

  val src_data = Mux(utils.isImm(cmd), utils.genImm(imm), src)

  utils.table.zipWithIndex.foreach { case ((reg, behavior), i) =>
    behavior match {
      case AlwaysUpdate(fn) =>
        csrRegs(i) := fn(extraInputIO)

      case ConditionalUpdate(fn) =>
        csrRegs(i) := fn(extraInputIO)
        when(en && write_access_allowed && addr === addr_map(i) && writable_vec(i)) {
          val wdata = utils.fn(cmd, csrRegs(i), src_data)
          csrRegs(i) := wdata
        }

      case NormalUpdate =>
        when(en && write_access_allowed) {
          when(addr === addr_map(i) && writable_vec(i)) {
            csrRegs(i) := utils.fn(cmd, csrRegs(i), src_data)
          }
        }
    }
  }

  rd := Mux(
    en,
    MuxCase(
      0.U(p(XLen).W),
      csrTable.zipWithIndex.map { case (_, i) =>
        (addr === addr_map(i)) -> csrRegs(i)
      }
    ),
    0.U(p(XLen).W)
  )
}
