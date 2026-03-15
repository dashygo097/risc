package arch.configs

import proto._
import arch.isa._
import vopts.mem.cache._
import scalapb.json4s.JsonFormat
import java.nio.file.{ Files, Paths }
import java.nio.charset.StandardCharsets

object RiscDump {
  def buildConfig(p: Parameters): RiscConfig =
    RiscConfig(
      isDebug = p(IsDebug),
      ifu = Some(
        IfuConfig(
          ibufferSize = p(IBufferSize),
        )
      ),
      bpu = Some(
        BpuConfig(
          btb = Some(
            BtbConfig(
              sets = p(BTBSets),
              ways = p(BTBWays),
              replPolicy = toProtoRepl(p(BTBReplPolicy)),
            )
          )
        )
      ),
      regfile = Some(
        RegfileConfig(
          numPhyRegs = p(NumPhyRegs),
          useBypass = p(IsRegfileUseBypass),
        )
      ),
      rob = Some(
        RobConfig(
          size = p(ROBSize),
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
      bus = Some(
        BusConfig(
          `type` = toProtoBus(p(arch.configs.BusType)),
          crossbarFifoDepth = p(BusCrossbarFifoDepthPerClient),
          addressMap = p(BusAddressMap),
        )
      ),
    )

  def dump(
    p: Parameters,
    configPath: String,
    isaPath: String,
    binPath: Option[String] = None,
    isaBinPath: Option[String] = None,
  ): Unit = {
    dumpConfig(p, configPath, binPath)
    dumpIsa(p, isaPath, isaBinPath)
  }

  def dumpConfig(p: Parameters, jsonPath: String, binPath: Option[String] = None): Unit = {
    val cfg = buildConfig(p)
    writeJson(cfg, jsonPath)
    binPath.foreach(bp => writeBin(cfg.toByteArray, bp))
    println(s"[RiscDump] config → $jsonPath")
  }

  def dumpIsa(p: Parameters, jsonPath: String, binPath: Option[String] = None): Unit = {
    val isa = IsaFactory.isa(p(ISA))
    writeJson(isa, jsonPath)
    binPath.foreach(bp => writeBin(isa.toByteArray, bp))
    println(s"[RiscDump] isa → $jsonPath")
  }

  private def writeJson(msg: scalapb.GeneratedMessage, path: String): Unit = {
    val json = JsonFormat.toJsonString(msg)
    Files.createDirectories(Paths.get(path).getParent)
    Files.write(Paths.get(path), json.getBytes(StandardCharsets.UTF_8))
  }

  private def writeBin(bytes: Array[Byte], path: String): Unit = {
    Files.createDirectories(Paths.get(path).getParent)
    Files.write(Paths.get(path), bytes)
  }

  private def toProtoRepl(p: ReplacementPolicy): ReplPolicy = p match {
    case Random    => ReplPolicy.REPL_POLICY_RANDOM
    case FIFO      => ReplPolicy.REPL_POLICY_FIFO
    case LFU       => ReplPolicy.REPL_POLICY_LFU
    case LRU       => ReplPolicy.REPL_POLICY_LRU
    case PseudoLRU => ReplPolicy.REPL_POLICY_PSEUDO_LRU
    case _         => ReplPolicy.REPL_POLICY_UNKNOWN
  }

  private def toProtoBus(s: String): BusType = s match {
    case "axil" => BusType.BUS_TYPE_AXIL
    case _      => BusType.BUS_TYPE_UNKNOWN
  }
}
