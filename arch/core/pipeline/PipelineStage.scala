package arch.core.pipeline

import arch.configs._
import chisel3._
import scala.collection.mutable

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

  // Public APIs
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

  def driveOpt(fieldName: String, value: => UInt): Unit =
    if (hasField(fieldName)) sin(fieldName) := value
}

class PipelineStageBuilder private[pipeline] (stageName: String) {

  private val _fields = mutable.ListBuffer[PipelineField]()

  def field(name: String, width: Int, initVal: Long = 0L): this.type = {
    _fields += PipelineField(name, width, initVal)
    this
  }

  def addField(f: PipelineField): this.type = {
    _fields += f
    this
  }

  def addFields(fs: Seq[PipelineField]): this.type = {
    _fields ++= fs
    this
  }

  def addFieldsWhen(cond: Boolean)(fs: => Seq[PipelineField]): this.type = {
    if (cond) _fields ++= fs
    this
  }

  def build()(implicit p: Parameters): PipelineStage =
    Module(new PipelineStage(stageName, _fields.toSeq))
}

object PipelineStageBuilder {
  def apply(name: String): PipelineStageBuilder = new PipelineStageBuilder(name)
}
