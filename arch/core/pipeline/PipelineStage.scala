package arch.core.pipeline

import arch.configs._
import chisel3._

class PipelineStage(
  stageName: String,
  fields: Seq[PipelineField]
)(implicit p: Parameters)
    extends Module
    with HasPipelineFields {

  override def desiredName: String = s"${p(ISA)}_$stageName"

  private val names = fields.map(_.name)
  require(
    names.distinct.length == names.length,
    s"PipelineStage '$stageName': duplicate field names: ${names.diff(names.distinct)}"
  )

  val stall = IO(Input(Bool()))
  val flush = IO(Input(Bool()))

  val sin: Map[String, UInt] = fields.map { f =>
    val port = IO(Input(UInt(f.width.W)))
    port.suggestName(s"${f.name}_i")
    f.name -> port
  }.toMap

  val sout: Map[String, UInt] = fields.map { f =>
    val port = IO(Output(UInt(f.width.W)))
    port.suggestName(s"${f.name}_o")
    val reg  = RegInit(f.initVal.U(f.width.W))
    when(flush) {
      reg := f.initVal.U
    }.elsewhen(!stall) {
      reg := sin(f.name)
    }
    port := reg
    f.name -> port
  }.toMap

  override val stageFields: Seq[PipelineField] = fields

  def apply(fieldName: String): UInt = {
    requireField(fieldName)
    sout(fieldName)
  }

  def get(fieldName: String): Option[UInt] =
    if (hasField(fieldName)) Some(sout(fieldName)) else None

  def drive(fieldName: String, value: UInt): Unit = {
    requireField(fieldName)
    sin(fieldName) := value
  }
}

object PipelineStage {
  def apply(
    name: String,
    legacy: Seq[(String, Int, Long)]
  )(implicit p: Parameters): PipelineStage =
    new PipelineStage(name, legacy.map(PipelineField.fromTuple))
}
