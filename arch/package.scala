package arch

package object configs {
  import proto._
  import proto.DeviceType._
  import proto.FunctionalUnitType._
  import isa._
  import vopts.mem.cache._
  import chisel3.util.BitPat

  // NOTE: User Options: You should only modify these parameters

  // --------------------------------------------
  // Architecture Parameters
  object ISA extends Field[IsaWrapper](RV32IM)

  // Ifu Parameters
  object IBufferSize extends Field[Int](8)
  object ResetVector extends Field[Long](0x80000000L)

  // Regfile Parameters
  object IsRegfileUseBypass extends Field[Boolean](true)
  object NumPhyRegs         extends Field[Int](64)

  // Scheduler Parameters
  object ScheduleType extends Field[String]("scoreboard")
  object IssueWidth   extends Field[Int](1)
  object FunctionalUnits
      extends Field[Seq[FunctionalUnitDescriptor]](
        Seq(
          FunctionalUnitDescriptor(name = "ALU_0", `type` = FUNCTIONAL_UNIT_TYPE_ALU),
          FunctionalUnitDescriptor(name = "MULT_0", `type` = FUNCTIONAL_UNIT_TYPE_MULT),
          FunctionalUnitDescriptor(name = "LSU_0", `type` = FUNCTIONAL_UNIT_TYPE_LSU),
          FunctionalUnitDescriptor(name = "BRU_0", `type` = FUNCTIONAL_UNIT_TYPE_BRU),
          FunctionalUnitDescriptor(name = "CSR", `type` = FUNCTIONAL_UNIT_TYPE_CSR),
        )
      )

  // Mult Parameters
  object MultPipelineStages extends Field[Int](2)

  // ROB Parameters
  object ROBSize extends Field[Int](16)

  // Branch Prediction
  object BTBWays       extends Field[Int](4)
  object BTBSets       extends Field[Int](16)
  object BTBReplPolicy extends Field[ReplacementPolicy](PseudoLRU)

  // Cache Parameters
  object L1ICacheWays       extends Field[Int](2)
  object L1ICacheSets       extends Field[Int](8)
  object L1ICacheLineSize   extends Field[Int](64) // in bytes
  object L1ICacheReplPolicy extends Field[ReplacementPolicy](LRU)

  object L1DCacheWays       extends Field[Int](4)
  object L1DCacheSets       extends Field[Int](8)
  object L1DCacheLineSize   extends Field[Int](16) // in bytes
  object L1DCacheReplPolicy extends Field[ReplacementPolicy](PseudoLRU)

  // Bus Parameters
  object BusType                       extends Field[String]("axif")
  object BusCrossbarFifoDepthPerClient extends Field[Int](4)

  object BusAddressMap
      extends Field[Seq[DeviceDescriptor]](
        Seq(
          DeviceDescriptor(name = "imem", `type` = DEVICE_TYPE_SRAM, base = 0x80000000L, size = 0x40000L),
          DeviceDescriptor(name = "dmem", `type` = DEVICE_TYPE_SRAM, base = 0x80040000L, size = 0x40000L),
          DeviceDescriptor(name = "uart", `type` = DEVICE_TYPE_UART, base = 0x10000000L, size = 0x1000L),
          DeviceDescriptor(name = "clint", `type` = DEVICE_TYPE_IRH, base = 0x20000000L, size = 0x10000L)
        )
      )
  // --------------------------------------------

  // NOTE: You should not modify the parameters below, as they are derived from the user options above
  // Derived Parameters
  object XLen        extends Field[Int](ISA().xlen)
  object ILen        extends Field[Int](ISA().ilen)
  object IAlign      extends Field[Int](ISA().iAlign)
  object NumArchRegs extends Field[Int](ISA().numArchRegs)
  object IsBigEndian extends Field[Boolean](ISA().isBigEndian)
  object Bubble      extends Field[BitPat](ISA().bubble)

  implicit val p: Parameters = Parameters.empty ++ Map(
    // ISA
    ISA         -> ISA(),
    XLen        -> XLen(),
    ILen        -> ILen(),
    IAlign      -> IAlign(),
    NumArchRegs -> NumArchRegs(),
    IsBigEndian -> IsBigEndian(),
    Bubble      -> Bubble(),

    // IFU
    IBufferSize -> IBufferSize(),
    ResetVector -> ResetVector(),

    // Regfile
    IsRegfileUseBypass -> IsRegfileUseBypass(),
    NumPhyRegs         -> NumPhyRegs(),

    // Scheduler
    ScheduleType    -> ScheduleType(),
    IssueWidth      -> IssueWidth(),
    FunctionalUnits -> FunctionalUnits(),

    // Mult
    MultPipelineStages -> MultPipelineStages(),

    // ROB
    ROBSize -> ROBSize(),

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
