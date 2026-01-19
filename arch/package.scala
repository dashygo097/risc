package arch

package object configs {
  import isa._
  import vopts.mem.cache._

  // User Options
  // You should only modify these parameters
  // Architecture Parameters
  object ISA     extends Field[String]("rv32i")
  object IsDebug extends Field[Boolean](true)

  // Core Parameters
  object IBufferSize        extends Field[Int](4)
  object IsRegfileUseBypass extends Field[Boolean](true)
  object NumPhyRegs         extends Field[Int](64)
  object ROBSize            extends Field[Int](16)
  object L1DCacheWays       extends Field[Int](2)
  object L1DCacheSets       extends Field[Int](16)
  object L1DCacheLineSize   extends Field[Int](16) // in bytes
  object L1DCacheReplPolicy extends Field[ReplacementPolicy](PseudoLRU)

  // System Parameters
  object BusType            extends Field[String]("axi")
  object FifoDepthPerClient extends Field[Int](4)
  object BusAddressMap
      extends Field[Seq[(Long, Long)]](
        Seq(
          (0x00000000L, 0x00001000L), // DRAM(IMEM)
          (0x80000000L, 0x80001000L), // DRAM(DMEM)
        )
      )

  // Derived Parameters
  object XLen        extends Field[Int](ISADefinition.xlen(ISA()))
  object ILen        extends Field[Int](ISADefinition.ilen(ISA()))
  object NumArchRegs extends Field[Int](ISADefinition.numArchRegs(ISA()))

  implicit val p: Parameters = Parameters.empty ++ Map(
    ISA         -> ISA.apply(),
    XLen        -> XLen.apply(),
    ILen        -> ILen.apply(),
    NumArchRegs -> NumArchRegs.apply()
  )
}

package object isa {}

package object core {}

package object system {}
