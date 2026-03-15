package arch.core.pipeline

import arch.configs._
import chisel3._

class PipelineStage(name: String, registers: Seq[(String, Int, Long)])(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_$name"

  val stall = IO(Input(Bool()))
  val flush = IO(Input(Bool()))

  val sin: Map[String, UInt] = registers.map { case (regName, width, regInit) =>
    val iport = IO(Input(UInt(width.W)))
    iport.suggestName(s"${regName}_i")

    regName -> iport
  }.toMap

  val sout: Map[String, UInt] = registers.map { case (regName, width, regInit) =>
    val oport = IO(Output(UInt(width.W)))
    oport.suggestName(s"${regName}_o")

    val reg = RegInit(regInit.U(width.W))
    when(flush) {
      reg := regInit.U
    }.elsewhen(!stall) {
      reg := sin(regName)
    }
    oport := reg

    regName -> oport
  }.toMap
}
