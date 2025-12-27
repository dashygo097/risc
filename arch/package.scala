package arch

package object configs {
  // User Options
  // You should only modify these parameters
  object ISA                extends Field[String]("rv32i")
  object IsRegfileUseBypass extends Field[Boolean](true)

  // Derived Parameters
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
    ILen -> ILen.apply(),
  )
}

package object isa {}

package object core {}
