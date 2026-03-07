package arch.configs

import proto._
import vopts.mem.cache._
import scalapb.json4s.JsonFormat
import java.nio.file.{ Files, Paths }
import java.nio.charset.StandardCharsets

object ConfigDump {

  def buildProto(p: Parameters): RiscConfig =
    RiscConfig(
      isDebug = p(IsDebug),
      isa = Some(
        Isa(
          name = p(ISA),
          xlen = p(XLen),
          ilen = p(ILen),
          numArchRegs = p(NumArchRegs),
          isBigEndian = p(IsBigEndian),
        )
      ),
      l1I = Some(
        CacheConfig(
          sets = p(L1ICacheSets),
          ways = p(L1ICacheWays),
          lineSize = p(L1ICacheLineSize),
          replPolicy = toProtoRepl(p(L1ICacheReplPolicy)),
        )
      ),
      l1D = Some(
        CacheConfig(
          sets = p(L1DCacheSets),
          ways = p(L1DCacheWays),
          lineSize = p(L1DCacheLineSize),
          replPolicy = toProtoRepl(p(L1DCacheReplPolicy)),
        )
      ),
      cpu = Some(
        CpuConfig(
          numPhyRegs = p(NumPhyRegs),
          ibufferSize = p(IBufferSize),
          robSize = p(ROBSize),
          regfileUseBypass = p(IsRegfileUseBypass),
        )
      ),
      bus = Some(
        BusConfig(
          `type` = toProtoBus(p(arch.configs.BusType)),
          crossbarFifoDepth = p(BusCrossbarFifoDepthPerClient),
          addressMap = p(BusAddressMap)
        )
      ),
    )

  def dump(p: Parameters, jsonPath: String, binPath: Option[String] = None): Unit = {
    val cfg = buildProto(p)

    val json = JsonFormat.toJsonString(cfg)
    Files.createDirectories(Paths.get(jsonPath).getParent)
    Files.write(Paths.get(jsonPath), json.getBytes(StandardCharsets.UTF_8))
    println(s"[ConfigDump] JSON  → $jsonPath")

    binPath.foreach { bp =>
      Files.createDirectories(Paths.get(bp).getParent)
      Files.write(Paths.get(bp), cfg.toByteArray)
      println(s"[ConfigDump] Binary → $bp")
    }
  }

  private def toProtoRepl(p: ReplacementPolicy): ReplPolicy = p match {
    case Random    => ReplPolicy.REPL_POLICY_RANDOM
    case LRU       => ReplPolicy.REPL_POLICY_LRU
    case PseudoLRU => ReplPolicy.REPL_POLICY_PSEUDO_LRU
    case _         => ReplPolicy.REPL_POLICY_UNKNOWN
  }

  private def toProtoBus(s: String): BusType = s match {
    case "axil" => BusType.BUS_TYPE_AXIL
    case _      => BusType.BUS_TYPE_UNKNOWN
  }
}
