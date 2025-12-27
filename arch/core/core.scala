package arch.core

import decoder._
import imm._
import common._
import alu._
import regfile._
import arch.configs._
import chisel3._

class RiscCore(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_cpu"

  val regfile_utils = RegfileUtilitiesFactory.get(p(ISA)) match {
    case Some(u) => u
    case None    => throw new Exception(s"Regfile utilities for ISA ${p(ISA)} not found!")
  }

  // TODO: Frontend Interface need to be impled as well as the cpu simulator
  // TEMPORARY Memory Interface
  val IMEM_ADDR = IO(Output(UInt(p(ALen).W)))
  val IMEM_INST = IO(Input(UInt(p(ILen).W)))

  val DMEM_ADDR       = IO(Output(UInt(p(ALen).W)))
  val DMEM_WRITE_DATA = IO(Output(UInt(p(XLen).W)))
  val DMEM_WRITE_STRB = IO(Output(UInt((p(XLen) / 8).W)))
  val DMEM_WRITE_EN   = IO(Output(Bool()))
  val DMEM_READ_DATA  = IO(Input(UInt(p(XLen).W)))
  val DMEM_READ_EN    = IO(Output(Bool()))

  val DEBUG_PC       = IO(Output(UInt(p(ALen).W)))
  val DEBUG_INST     = IO(Output(UInt(p(ILen).W)))
  val DEBUG_REG_WE   = IO(Output(Bool()))
  val DEBUG_REG_ADDR = IO(Output(UInt(regfile_utils.width.W)))
  val DEBUG_REG_DATA = IO(Output(UInt(p(XLen).W)))

  // Modules
  val decoder = Module(new Decoder)
  val imm_gen = Module(new ImmGen)
  val regfile = Module(new Regfile)
  val id_fwd  = Module(new IDForwardingUnit)
  val ex_fwd  = Module(new EXForwardingUnit)
  val alu     = Module(new ALU)

}
