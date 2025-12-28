package arch.core

import common._
import decoder._
import regfile._
import arch.configs._
import chisel3._

class InstructionFetchBundle(implicit p: Parameters) extends Bundle {
  val pc    = UInt(p(XLen).W)
  val instr = UInt(p(ILen).W)
}

class InstructionDecodeBundle(implicit p: Parameters) extends Bundle {
  val decode_utils = DecoderUtilitiesFactory.get(p(ISA)) match {
    case Some(u) => u
    case None    => throw new Exception(s"Decoder utilities for ISA ${p(ISA)} not found!")
  }

  val regfile_utils = RegfileUtilitiesFactory.get(p(ISA)) match {
    case Some(u) => u
    case None    => throw new Exception(s"Regfile utilities for ISA ${p(ISA)} not found!")
  }

  val decoded_output = new DecodedOutput
  val instr          = UInt(p(ILen).W)
  val pc             = UInt(p(XLen).W)
  val rs1            = UInt(regfile_utils.width.W)
  val rs1_data       = UInt(p(XLen).W)
  val rs2            = UInt(regfile_utils.width.W)
  val rs2_data       = UInt(p(XLen).W)
  val rd             = UInt(regfile_utils.width.W)
}

class ExcutionBundle(implicit p: Parameters) extends Bundle {
  val regfile_utils = RegfileUtilitiesFactory.get(p(ISA)) match {
    case Some(u) => u
    case None    => throw new Exception(s"Regfile utilities for ISA ${p(ISA)} not found!")
  }

  val alu_result = UInt(p(XLen).W)
  val instr      = UInt(p(ILen).W)
  val pc         = UInt(p(XLen).W)
  val rd         = UInt(regfile_utils.width.W)
  val regwrite   = Bool()
}

class MemoryBundle(implicit p: Parameters) extends Bundle {
  val regfile_utils = RegfileUtilitiesFactory.get(p(ISA)) match {
    case Some(u) => u
    case None    => throw new Exception(s"Regfile utilities for ISA ${p(ISA)} not found!")
  }

  val wb_data  = UInt(p(XLen).W)
  val instr    = UInt(p(ILen).W)
  val pc       = UInt(p(XLen).W)
  val rd       = UInt(regfile_utils.width.W)
  val regwrite = Bool()
}

class IF_ID(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_if_id"

  val STALL = IO(Input(Bool()))
  val FLUSH = IO(Input(Bool()))
  val IF    = IO(Input(new InstructionFetchBundle))
  val ID    = IO(Output(new InstructionFetchBundle))

  val stage = Module(
    new PipelineStage(new InstructionFetchBundle)
  )

  stage.stall := STALL
  stage.flush := FLUSH
  stage.sin   := IF
  ID          := stage.sout
}

class ID_EX(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_id_ex"

  val STALL = IO(Input(Bool()))
  val FLUSH = IO(Input(Bool()))
  val ID    = IO(Input(new InstructionDecodeBundle))
  val EX    = IO(Output(new InstructionDecodeBundle))

  val stage = Module(
    new PipelineStage(new InstructionDecodeBundle)
  )

  stage.stall := STALL
  stage.flush := FLUSH
  stage.sin   := ID
  EX          := stage.sout
}

class EX_MEM(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_ex_mem"

  val STALL = IO(Input(Bool()))
  val FLUSH = IO(Input(Bool()))
  val EX    = IO(Input(new ExcutionBundle))
  val MEM   = IO(Output(new ExcutionBundle))

  val stage = Module(
    new PipelineStage(new ExcutionBundle)
  )

  stage.stall := STALL
  stage.flush := FLUSH
  stage.sin   := EX
  MEM         := stage.sout
}

class MEM_WB(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_mem_wb"

  val STALL = IO(Input(Bool()))
  val FLUSH = IO(Input(Bool()))
  val MEM   = IO(Input(new MemoryBundle))
  val WB    = IO(Output(new MemoryBundle))

  val stage = Module(
    new PipelineStage(new MemoryBundle)
  )

  stage.stall := STALL
  stage.flush := FLUSH
  stage.sin   := MEM
  WB          := stage.sout
}
