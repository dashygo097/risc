package arch

package object configs {
  // User Options
  // You should only modify these parameters
  // Architecture Parameters
  object ISA     extends Field[String]("rv32i")
  object IsDebug extends Field[Boolean](true)

  // Core Parameters
  object IBufferSize        extends Field[Int](4)
  object IsRegfileUseBypass extends Field[Boolean](true)

  // System Parameters
  object BusType extends Field[String]("axi")
  object BusAddressMap
      extends Field[Seq[(Long, Long)]](
        Seq(
          (0x00000000L, 0xffffffffL), // Memory
        )
      )

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

package object system {}
