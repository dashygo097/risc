package arch.core.pipeline

trait HasPipelineFields {

  val stageFields: Seq[PipelineField]

  private lazy val fieldMap: Map[String, PipelineField] =
    stageFields.map(f => f.name -> f).toMap

  def hasField(name: String): Boolean = fieldMap.contains(name)

  def fieldWidth(name: String): Int = {
    requireField(name)
    fieldMap(name).width
  }

  def fieldInit(name: String): Long = {
    requireField(name)
    fieldMap(name).initVal
  }

  def requireField(name: String): Unit =
    require(
      hasField(name),
      s"Pipeline stage does not contain field '$name'. " +
        s"Available: ${stageFields.map(_.name).mkString(", ")}"
    )

  def fieldNames: Seq[String] = stageFields.map(_.name)
}
