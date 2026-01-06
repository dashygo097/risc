package arch.core

import common._
import decoder._
import regfile._
import lsu._
import arch.configs._
import chisel3._

class InstructionFetchBundle(implicit p: Parameters) extends Bundle {
  val pc = UInt(p(XLen).W)
}

class InstructionDecodeBundle(implicit p: Parameters) extends Bundle {
  val regfile_utils = RegfileUtilitiesFactory.getOrThrow(p(ISA))

  val pc             = UInt(p(XLen).W)
  val rd             = UInt(regfile_utils.width.W)
  val decoded_output = new DecodedOutput
  val rs1            = UInt(regfile_utils.width.W)
  val rs1_data       = UInt(p(XLen).W)
  val rs2            = UInt(regfile_utils.width.W)
  val rs2_data       = UInt(p(XLen).W)
  val imm            = UInt(p(XLen).W)
}

class ExcutionBundle(implicit p: Parameters) extends Bundle {
  val regfile_utils = RegfileUtilitiesFactory.getOrThrow(p(ISA))
  val lsu_utils     = LsuUtilitiesFactory.getOrThrow(p(ISA))

  val pc         = UInt(p(XLen).W)
  val rd         = UInt(regfile_utils.width.W)
  val alu_result = UInt(p(XLen).W)
  val rs2_data   = UInt(p(XLen).W)
  val regwrite   = Bool()
  val lsu        = Bool()
  val lsu_cmd    = UInt(lsu_utils.cmdWidth.W)
}

class MemoryBundle(implicit p: Parameters) extends Bundle {
  val regfile_utils = RegfileUtilitiesFactory.getOrThrow(p(ISA))

  val pc       = UInt(p(XLen).W)
  val rd       = UInt(regfile_utils.width.W)
  val regwrite = Bool()
  val wb_data  = UInt(p(XLen).W)
}

class IF_ID(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_if_id"

  val STALL = IO(Input(Bool()))
  val FLUSH = IO(Input(Bool()))
  val BUSY  = IO(Output(Bool()))

  val IF_INSTR = IO(Input(UInt(p(ILen).W)))
  val IF       = IO(Input(new InstructionFetchBundle))
  val ID_INSTR = IO(Output(UInt(p(ILen).W)))
  val ID       = IO(Output(new InstructionFetchBundle))

  val stage = Module(
    new PipelineStage(new InstructionFetchBundle)
  )

  stage.stall := STALL
  stage.flush := FLUSH
  BUSY        := stage.busy

  stage.sin_instr := IF_INSTR
  stage.sin_extra := IF
  ID_INSTR        := stage.sout_instr
  ID              := stage.sout_extra
}

class ID_EX(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_id_ex"

  val STALL = IO(Input(Bool()))
  val FLUSH = IO(Input(Bool()))
  val BUSY  = IO(Output(Bool()))

  val ID_INSTR = IO(Input(UInt(p(ILen).W)))
  val ID       = IO(Input(new InstructionDecodeBundle))
  val EX_INSTR = IO(Output(UInt(p(ILen).W)))
  val EX       = IO(Output(new InstructionDecodeBundle))

  val stage = Module(
    new PipelineStage(new InstructionDecodeBundle)
  )

  stage.stall := STALL
  stage.flush := FLUSH
  BUSY        := stage.busy

  stage.sin_instr := ID_INSTR
  stage.sin_extra := ID
  EX_INSTR        := stage.sout_instr
  EX              := stage.sout_extra
}

class EX_MEM(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_ex_mem"

  val STALL = IO(Input(Bool()))
  val FLUSH = IO(Input(Bool()))
  val BUSY  = IO(Output(Bool()))

  val EX_INSTR  = IO(Input(UInt(p(ILen).W)))
  val EX        = IO(Input(new ExcutionBundle))
  val MEM_INSTR = IO(Output(UInt(p(ILen).W)))
  val MEM       = IO(Output(new ExcutionBundle))

  val stage = Module(
    new PipelineStage(new ExcutionBundle)
  )

  stage.stall := STALL
  stage.flush := FLUSH
  BUSY        := stage.busy

  stage.sin_instr := EX_INSTR
  stage.sin_extra := EX
  MEM_INSTR       := stage.sout_instr
  MEM             := stage.sout_extra
}

class MEM_WB(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_mem_wb"

  val STALL = IO(Input(Bool()))
  val FLUSH = IO(Input(Bool()))
  val BUSY  = IO(Output(Bool()))

  val MEM_INSTR = IO(Input(UInt(p(ILen).W)))
  val MEM       = IO(Input(new MemoryBundle))
  val WB_INSTR  = IO(Output(UInt(p(ILen).W)))
  val WB        = IO(Output(new MemoryBundle))

  val stage = Module(
    new PipelineStage(new MemoryBundle)
  )

  stage.stall := STALL
  stage.flush := FLUSH
  BUSY        := stage.busy

  stage.sin_instr := MEM_INSTR
  stage.sin_extra := MEM
  WB              := stage.sout_extra
  WB_INSTR        := stage.sout_instr
}
