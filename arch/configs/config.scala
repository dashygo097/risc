package arch.configs

abstract class Config(
  val site: Map[Any, Any] = Map.empty,
  val knobs: Map[String, Any] = Map.empty
) {
  def toInstance(prev: Parameters): Parameters =
    Parameters(site, Some(prev))

  def ++(other: Config): Config =
    new Config(site ++ other.site, knobs ++ other.knobs) {}
}

class View[T](pname: Parameters => T) {
  def apply(p: Parameters): T = pname(p)
}
