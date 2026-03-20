package arch

package object configs {
  import proto._
  import proto.DeviceType._
  import isa._
  import vopts.mem.cache._
  import chisel3.util.BitPat

  // NOTE: User Options: You should only modify these parameters

  // --------------------------------------------
  // Architecture Parameters
  object ISA extends Field[String]("rv32i")

  // CPU Parameters
  object IBufferSize        extends Field[Int](4)
  object IsRegfileUseBypass extends Field[Boolean](true)
  object NumPhyRegs         extends Field[Int](64)
  object ROBSize            extends Field[Int](16)

  // Branch Prediction
  object BTBWays       extends Field[Int](4)
  object BTBSets       extends Field[Int](16)
  object BTBReplPolicy extends Field[ReplacementPolicy](PseudoLRU)

  // Cache Parameters
  object L1ICacheWays       extends Field[Int](2)
  object L1ICacheSets       extends Field[Int](8)
  object L1ICacheLineSize   extends Field[Int](16) // in bytes
  object L1ICacheReplPolicy extends Field[ReplacementPolicy](LRU)

  object L1DCacheWays       extends Field[Int](4)
  object L1DCacheSets       extends Field[Int](8)
  object L1DCacheLineSize   extends Field[Int](16) // in bytes
  object L1DCacheReplPolicy extends Field[ReplacementPolicy](PseudoLRU)

  // Bus Parameters
  object BusType                       extends Field[String]("axil")
  object BusCrossbarFifoDepthPerClient extends Field[Int](4)

  object BusAddressMap
      extends Field[Seq[DeviceDescriptor]](
        Seq(
          DeviceDescriptor(name = "imem", `type` = DEVICE_TYPE_SRAM, base = 0x00000000L, size = 0x1000L),
          DeviceDescriptor(name = "dmem", `type` = DEVICE_TYPE_SRAM, base = 0x80000000L, size = 0x4000L),
          DeviceDescriptor(name = "uart", `type` = DEVICE_TYPE_UART, base = 0x10000000L, size = 0x100L),
        )
      )
  // --------------------------------------------

  // NOTE: You should not modify the parameters below, as they are derived from the user options above
  // Derived Parameters
  object XLen        extends Field[Int](IsaFactory.xlen(ISA()))
  object ILen        extends Field[Int](IsaFactory.ilen(ISA()))
  object IAlign      extends Field[Int](IsaFactory.iAlign(ISA()))
  object NumArchRegs extends Field[Int](IsaFactory.numArchRegs(ISA()))
  object IsBigEndian extends Field[Boolean](IsaFactory.isBigEndian(ISA()))
  object Bubble      extends Field[BitPat](IsaFactory.bubble(ISA()))

  implicit val p: Parameters = Parameters.empty ++ Map(
    // ISA
    ISA         -> ISA(),
    XLen        -> XLen(),
    ILen        -> ILen(),
    IAlign      -> IAlign(),
    NumArchRegs -> NumArchRegs(),
    IsBigEndian -> IsBigEndian(),
    Bubble      -> Bubble(),

    // Core
    IBufferSize        -> IBufferSize(),
    IsRegfileUseBypass -> IsRegfileUseBypass(),
    NumPhyRegs         -> NumPhyRegs(),
    ROBSize            -> ROBSize(),

    // Cache
    L1ICacheWays       -> L1ICacheWays(),
    L1ICacheSets       -> L1ICacheSets(),
    L1ICacheLineSize   -> L1ICacheLineSize(),
    L1ICacheReplPolicy -> L1ICacheReplPolicy(),
    L1DCacheWays       -> L1DCacheWays(),
    L1DCacheSets       -> L1DCacheSets(),
    L1DCacheLineSize   -> L1DCacheLineSize(),
    L1DCacheReplPolicy -> L1DCacheReplPolicy(),

    // Bus
    BusType                       -> BusType(),
    BusCrossbarFifoDepthPerClient -> BusCrossbarFifoDepthPerClient(),
    BusAddressMap                 -> BusAddressMap(),
  )
}
