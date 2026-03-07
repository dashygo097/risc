package arch

package object configs {
  import isa._
  import system._
  import vopts.mem.cache._
  import chisel3.util.BitPat

  // NOTE: User Options: You should only modify these parameters

  // --------------------------------------------
  // Architecture Parameters
  object ISA     extends Field[String]("rv32i", "isa")
  object IsDebug extends Field[Boolean](true, "isa")

  // Core Parameters
  object IBufferSize        extends Field[Int](4, "cpu")
  object IsRegfileUseBypass extends Field[Boolean](true, "cpu")
  object NumPhyRegs         extends Field[Int](64, "cpu")
  object ROBSize            extends Field[Int](16, "cpu")

  // Cache
  object L1ICacheWays       extends Field[Int](2, "cache.l1i")
  object L1ICacheSets       extends Field[Int](8, "cache.l1i")
  object L1ICacheLineSize   extends Field[Int](16, "cache.l1i") // in bytes
  object L1ICacheReplPolicy extends Field[ReplacementPolicy](LRU, "cache.l1i")

  object L1DCacheWays       extends Field[Int](4, "cache.l1d")
  object L1DCacheSets       extends Field[Int](8, "cache.l1d")
  object L1DCacheLineSize   extends Field[Int](16, "cache.l1d") // in bytes
  object L1DCacheReplPolicy extends Field[ReplacementPolicy](PseudoLRU, "cache.l1d")

  // System Parameters
  object BusType                       extends Field[String]("axil", "bus")
  object BusCrossbarFifoDepthPerClient extends Field[Int](4, "bus")

  object BusAddressMap
      extends Field[Seq[DeviceDescriptor]](
        Seq(
          DeviceDescriptor("imem", "memory", 0x00000000L, 0x1000L),
          DeviceDescriptor("dmem", "memory", 0x80000000L, 0x4000L)
        ),
        "bus"
      )
  // --------------------------------------------

  // NOTE: You should not modify the parameters below, as they are derived from the user options above
  // Derived Parameters
  object XLen        extends Field[Int](ISADefinition.xlen(ISA()), "isa")
  object ILen        extends Field[Int](ISADefinition.ilen(ISA()), "isa")
  object NumArchRegs extends Field[Int](ISADefinition.numArchRegs(ISA()), "isa")
  object IsBigEndian extends Field[Boolean](ISADefinition.isBigEndian(ISA()), "isa")
  object Bubble      extends Field[BitPat](ISADefinition.bubble(ISA()), "isa")

  implicit val p: Parameters = Parameters.empty ++ Map(
    ISA                           -> ISA(),
    IsDebug                       -> IsDebug(),
    XLen                          -> XLen(),
    ILen                          -> ILen(),
    NumArchRegs                   -> NumArchRegs(),
    IsBigEndian                   -> IsBigEndian(),
    Bubble                        -> Bubble(),
    IBufferSize                   -> IBufferSize(),
    IsRegfileUseBypass            -> IsRegfileUseBypass(),
    NumPhyRegs                    -> NumPhyRegs(),
    ROBSize                       -> ROBSize(),
    L1ICacheWays                  -> L1ICacheWays(),
    L1ICacheSets                  -> L1ICacheSets(),
    L1ICacheLineSize              -> L1ICacheLineSize(),
    L1ICacheReplPolicy            -> L1ICacheReplPolicy(),
    L1DCacheWays                  -> L1DCacheWays(),
    L1DCacheSets                  -> L1DCacheSets(),
    L1DCacheLineSize              -> L1DCacheLineSize(),
    L1DCacheReplPolicy            -> L1DCacheReplPolicy(),
    BusType                       -> BusType(),
    BusCrossbarFifoDepthPerClient -> BusCrossbarFifoDepthPerClient(),
    BusAddressMap                 -> BusAddressMap()
  )

  ConfigDump.dump(p, "build/config.json")
}

package object isa {}

package object core {}

package object system {}
