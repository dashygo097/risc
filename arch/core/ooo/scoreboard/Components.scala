package arch.core.ooo

import arch.configs._
import chisel3._
import chisel3.util.log2Ceil

import scala.collection.mutable

sealed trait FULatency
case object SingleCycle              extends FULatency
case class FixedLatency(cycles: Int) extends FULatency
case object VariableLatency          extends FULatency

trait FUDescriptor {
  def name: String
  def latency: FULatency
}

object FURegistry {
  private val _entries = mutable.LinkedHashMap.empty[String, (FUDescriptor, Int)]
  private var _nextId  = 0

  def register(desc: FUDescriptor): Int =
    _entries.get(desc.name) match {
      case Some((_, id)) => id
      case None          =>
        val id = _nextId
        _entries(desc.name) = (desc, id)
        _nextId += 1
        id
    }

  def id(name: String): Int =
    _entries
      .getOrElse(
        name,
        throw new NoSuchElementException(
          s"FU '$name' not registered. Available: ${_entries.keys.mkString(", ")}"
        )
      )
      ._2

  def descriptor(name: String): FUDescriptor = _entries(name)._1
  def numFUs: Int                            = _entries.size
  def all: Seq[(FUDescriptor, Int)]          = _entries.values.toSeq
  def names: Seq[String]                     = _entries.keys.toSeq

  def clear(): Unit = {
    _entries.clear()
    _nextId = 0
  }
}

class InstructionStatus extends Bundle {
  val issue        = Bool()
  val read_oper    = Bool()
  val exec_comp    = Bool()
  val write_result = Bool()
}

class FunctionalUnitStatus(implicit p: Parameters) extends Bundle {
  val busy      = Bool()
  val op        = UInt(p(ILen).W)
  val rd        = UInt(log2Ceil(p(NumArchRegs)).W)
  val rs1       = UInt(log2Ceil(p(NumArchRegs)).W)
  val rs2       = UInt(log2Ceil(p(NumArchRegs)).W)
  val rs1_prod  = UInt(log2Ceil(FURegistry.numFUs + 1).W)
  val rs2_prod  = UInt(log2Ceil(FURegistry.numFUs + 1).W)
  val rs1_ready = Bool()
  val rs2_ready = Bool()
}

class RegisterResultStatus(implicit p: Parameters) extends Bundle {
  val rs   = UInt(log2Ceil(p(NumArchRegs)).W)
  val prod = UInt(log2Ceil(FURegistry.numFUs + 1).W)
}

class ScoreboardIO(implicit p: Parameters) extends Bundle {
  private val NUM_FUS  = FURegistry.numFUs
  private val regWidth = log2Ceil(p(NumArchRegs))
  private val fuWidth  = log2Ceil(NUM_FUS)

  val issue_valid = Input(Bool())
  val issue_instr = Input(UInt(p(ILen).W))
  val issue_rd    = Input(UInt(regWidth.W))
  val issue_rs1   = Input(UInt(regWidth.W))
  val issue_rs2   = Input(UInt(regWidth.W))
  val issue_fu_id = Input(UInt(fuWidth.W))

  val issue_ready = Output(Bool())

  val rs1_ready = Output(Bool())
  val rs2_ready = Output(Bool())

  val fu_done = Input(Vec(NUM_FUS, Bool()))
  val fu_rd   = Input(Vec(NUM_FUS, UInt(regWidth.W)))

  val fu_status    = Output(Vec(NUM_FUS, new FunctionalUnitStatus))
  val reg_status   = Output(Vec(p(NumArchRegs), new RegisterResultStatus))
  val instr_status = Output(Vec(NUM_FUS, new InstructionStatus))
}
