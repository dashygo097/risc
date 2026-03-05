package arch

package object configs {
  import isa._
  import vopts.mem.cache._
  import chisel3.util.BitPat

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

  // Cache
  object L1ICacheWays       extends Field[Int](2)
  object L1ICacheSets       extends Field[Int](8)
  object L1ICacheLineSize   extends Field[Int](16) // in bytes
  object L1ICacheReplPolicy extends Field[ReplacementPolicy](LRU)

  object L1DCacheWays       extends Field[Int](4)
  object L1DCacheSets       extends Field[Int](8)
  object L1DCacheLineSize   extends Field[Int](16) // in bytes
  object L1DCacheReplPolicy extends Field[ReplacementPolicy](PseudoLRU)

  // System Parameters
  object BusType            extends Field[String]("axil")
  object FifoDepthPerClient extends Field[Int](4)
  object BusAddressMap
      extends Field[Seq[(Long, Long)]](
        Seq(
          (0x00000000L, 0x00001000L), // DRAM(IMEM)
          (0x80000000L, 0x80004000L), // DRAM(DMEM)
        )
      )

  // Derived Parameters
  object XLen        extends Field[Int](ISADefinition.xlen(ISA()))
  object ILen        extends Field[Int](ISADefinition.ilen(ISA()))
  object NumArchRegs extends Field[Int](ISADefinition.numArchRegs(ISA()))
  object IsBigEndian extends Field[Boolean](ISADefinition.isBigEndian(ISA()))
  object Bubble      extends Field[BitPat](ISADefinition.bubble(ISA()))

  implicit val p: Parameters = Parameters.empty ++ Map(
    ISA                -> ISA.apply(),
    IsDebug            -> IsDebug.apply(),
    XLen               -> XLen.apply(),
    ILen               -> ILen.apply(),
    NumArchRegs        -> NumArchRegs.apply(),
    IsBigEndian        -> IsBigEndian.apply(),
    Bubble             -> Bubble.apply(),
    IBufferSize        -> IBufferSize.apply(),
    IsRegfileUseBypass -> IsRegfileUseBypass.apply(),
    NumPhyRegs         -> NumPhyRegs.apply(),
    ROBSize            -> ROBSize.apply(),
    L1ICacheWays       -> L1ICacheWays.apply(),
    L1ICacheSets       -> L1ICacheSets.apply(),
    L1ICacheLineSize   -> L1ICacheLineSize.apply(),
    L1ICacheReplPolicy -> L1ICacheReplPolicy.apply(),
    L1DCacheWays       -> L1DCacheWays.apply(),
    L1DCacheSets       -> L1DCacheSets.apply(),
    L1DCacheLineSize   -> L1DCacheLineSize.apply(),
    L1DCacheReplPolicy -> L1DCacheReplPolicy.apply(),
    BusType            -> BusType.apply(),
    FifoDepthPerClient -> FifoDepthPerClient.apply(),
    BusAddressMap      -> BusAddressMap.apply()
  )

  ConfigDump.dump(p, "build/config.json")
}

package object isa {}

package object core {}

package object system {}
