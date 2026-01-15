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

  val csrTable     = utils.table
  val addr_map     = csrTable.map(_.addr.U)
  val writable_vec = VecInit(csrTable.map(_.writable.B))

  val csrRegs = csrTable.zipWithIndex.map { case (reg, i) =>
    val regInst = RegInit(reg.initValue.U(p(XLen).W))
    regInst.suggestName(reg.name)
    regInst
  }

  val addr_match           = Wire(Bool())
  val write_access_allowed = Wire(Bool())

  addr_match := addr_map.map(_ === addr).reduce(_ || _)

  write_access_allowed := addr_match &&
    MuxCase(
      false.B,
      csrTable.zipWithIndex.map { case (reg, i) =>
        (addr === addr_map(i)) -> writable_vec(i)
      }
    )

  val src_data = Mux(utils.isImm(cmd), utils.genImm(imm), src)
  when(en && write_access_allowed) {
    for (i <- 0 until csrTable.length)
      when(addr === addr_map(i)) {
        when(writable_vec(i)) {
          val csr_rdata = csrRegs(i)
          val csr_wdata = utils.fn(cmd, csr_rdata, src_data)
          csrRegs(i) := csr_wdata
        }
      }
  }

  when(en) {
    rd := MuxCase(
      0.U(p(XLen).W),
      csrTable.zipWithIndex.map { case (reg, i) =>
        (addr === addr_map(i)) -> csrRegs(i)
      }
    )
  }.otherwise {
    rd := 0.U(p(XLen).W)
  }

  // TODO: Special handling for some CSRs
}
