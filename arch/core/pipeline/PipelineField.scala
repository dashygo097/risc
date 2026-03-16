package arch.core.pipeline

final case class PipelineField(
  name: String,
  width: Int,
  initVal: Long = 0L
) {
  require(width > 0, s"PipelineField '$name': width must be > 0")
  require(name.nonEmpty, "PipelineField name must not be empty")

  override def toString: String = s"$name[$width](init=$initVal)"
}

object PipelineField {
  def fromTuple(t: (String, Int, Long)): PipelineField =
    PipelineField(t._1, t._2, t._3)
}
