package arch

package object configs {
  object ISA extends Field[String]("rv32i")
  object XLen
      extends Field[Int](
        ISA() match {
          case "rv32i" => 32
          case other   => throw new Exception(s"Unsupported ISA: $other")
        }
      )

  object ILen
      extends Field[Int](
        ISA() match {
          case "rv32i" => 32
          case other   => throw new Exception(s"Unsupported ISA: $other")
        }
      )

  implicit val p: Parameters = Parameters.empty ++ Map(
    ISA  -> ISA.apply(),
    XLen -> XLen.apply(),
    ILen -> ILen.apply()
  )
}
