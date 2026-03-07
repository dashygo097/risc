#pragma once

#include "./isa/isa.hh"
#include "./logger.hh"
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <nlohmann/json.hpp>
#include <string>
#include <vector>

namespace demu {
using namespace isa;
using namespace nlohmann;

enum class BusType { AXILite, Unknown };

inline BusType parseBusType(const std::string &s) {
  if (s == "axil")
    return BusType::AXILite;
  return BusType::Unknown;
}

inline std::string busTypeToString(BusType t) {
  switch (t) {
  case BusType::AXILite:
    return "axil";
  default:
    return "unknown";
  }
}

enum class ReplPolicy { LRU, PseudoLRU, Random, Unknown };

inline ReplPolicy parseReplPolicy(const std::string &s) {
  if (s == "LRU")
    return ReplPolicy::LRU;
  if (s == "PseudoLRU")
    return ReplPolicy::PseudoLRU;
  if (s == "Random")
    return ReplPolicy::Random;
  return ReplPolicy::Unknown;
}

struct MemRegion {
  std::string device;
  std::string type;
  addr_t base{0};
  addr_t size{0};

  [[nodiscard]] addr_t end() const noexcept { return base + size; }
  [[nodiscard]] bool contains(addr_t addr) const noexcept {
    return addr >= base && addr < end();
  }
};

struct IsaConfig {
  std::string isa_name{"rv32i"};
  uint32_t xlen{32};
  uint32_t ilen{32};
  uint32_t num_arch_regs{32};
  bool is_big_endian{false};
  std::string bubble{"0x13"};
  bool is_debug{false};
};

struct CacheConfig {
  uint32_t sets{8};
  uint32_t ways{2};
  uint32_t line_size{16};
  ReplPolicy repl_policy{ReplPolicy::LRU};

  [[nodiscard]] uint32_t line_words(uint32_t word_bytes = 4) const noexcept {
    return line_size / word_bytes;
  }
  [[nodiscard]] uint32_t capacity_bytes() const noexcept {
    return sets * ways * line_size;
  }
};

struct CpuConfig {
  uint32_t num_phy_regs{64};
  uint32_t ibuffer_size{4};
  uint32_t rob_size{16};
  bool use_bypass{true};
};

struct BusConfig {
  BusType type{BusType::AXILite};
  uint32_t crossbar_fifo_depth{4};
  std::vector<MemRegion> address_map;

  [[nodiscard]] const MemRegion *
  find(const std::string &device) const noexcept {
    for (const auto &r : address_map)
      if (r.device == device)
        return &r;
    return nullptr;
  }

  [[nodiscard]] const MemRegion *find_by_addr(addr_t addr) const noexcept {
    for (const auto &r : address_map)
      if (r.contains(addr))
        return &r;
    return nullptr;
  }
};

class RiscConfig {
public:
  RiscConfig() { load(get_config_path()); }

  explicit RiscConfig(const std::string &path) { load(path); }

  [[nodiscard]] const IsaConfig &isa() const noexcept { return isa_; }
  [[nodiscard]] const CacheConfig &l1i() const noexcept { return l1i_; }
  [[nodiscard]] const CacheConfig &l1d() const noexcept { return l1d_; }
  [[nodiscard]] const CpuConfig &cpu() const noexcept { return cpu_; }
  [[nodiscard]] const BusConfig &bus() const noexcept { return bus_; }

  [[nodiscard]] const MemRegion *imem() const noexcept {
    return bus_.find("imem");
  }
  [[nodiscard]] const MemRegion *dmem() const noexcept {
    return bus_.find("dmem");
  }

  [[nodiscard]] bool is_valid() const noexcept { return valid_; }

  bool validate() const {
    bool ok = true;
    if (!imem()) {
      DEMU_ERROR("RiscConfig: 'imem' not found in bus.BusAddressMap");
      ok = false;
    }
    if (!dmem()) {
      DEMU_ERROR("RiscConfig: 'dmem' not found in bus.BusAddressMap");
      ok = false;
    }
    if (isa_.xlen != 32 && isa_.xlen != 64) {
      DEMU_ERROR("RiscConfig: invalid isa.XLen = {}", isa_.xlen);
      ok = false;
    }
    if (bus_.type == BusType::Unknown) {
      DEMU_ERROR("RiscConfig: unknown bus.BusType");
      ok = false;
    }
    if (l1i_.line_size == 0 || (l1i_.line_size & (l1i_.line_size - 1)) != 0) {
      DEMU_ERROR("RiscConfig: l1i line_size must be a non-zero power of 2");
      ok = false;
    }
    if (l1d_.line_size == 0 || (l1d_.line_size & (l1d_.line_size - 1)) != 0) {
      DEMU_ERROR("RiscConfig: l1d line_size must be a non-zero power of 2");
      ok = false;
    }
    return ok;
  }

  void dump() const {
    DEMU_INFO("=== RiscConfig ===");
    DEMU_INFO("  config: {}", config_path_);
    DEMU_INFO("  [isa]   {} xlen={} ilen={} archregs={} endian={} debug={}",
              isa_.isa_name, isa_.xlen, isa_.ilen, isa_.num_arch_regs,
              isa_.is_big_endian ? "big" : "little", isa_.is_debug);
    DEMU_INFO("  [cpu]   phyregs={} ibuf={} rob={} bypass={}",
              cpu_.num_phy_regs, cpu_.ibuffer_size, cpu_.rob_size,
              cpu_.use_bypass);
    DEMU_INFO("  [l1i]   {}s {}w {}B/line {} cap={}B", l1i_.sets, l1i_.ways,
              l1i_.line_size,
              l1i_.repl_policy == ReplPolicy::LRU ? "LRU" : "PLRU",
              l1i_.capacity_bytes());
    DEMU_INFO("  [l1d]   {}s {}w {}B/line {} cap={}B", l1d_.sets, l1d_.ways,
              l1d_.line_size,
              l1d_.repl_policy == ReplPolicy::LRU         ? "LRU"
              : l1d_.repl_policy == ReplPolicy::PseudoLRU ? "PLRU"
                                                          : "Rand",
              l1d_.capacity_bytes());
    DEMU_INFO("  [bus]   type={} fifo={}", busTypeToString(bus_.type),
              bus_.crossbar_fifo_depth);
    for (const auto &r : bus_.address_map) {
      DEMU_INFO("    {:6s} {:8s} base=0x{:08x} size=0x{:x} end=0x{:08x}",
                r.device, r.type, r.base, r.size, r.end());
    }
    DEMU_INFO("==================");
  }

private:
  IsaConfig isa_;
  CacheConfig l1i_;
  CacheConfig l1d_;
  CpuConfig cpu_;
  BusConfig bus_;
  std::string config_path_;
  bool valid_{false};

  static std::string get_config_path() {
#ifdef RTL_CONFIG_FILE
    return std::string(RTL_CONFIG_FILE);
#else
    DEMU_ERROR("RTL_CONFIG_FILE not defined in compile definitions");
    return "";
#endif
  }

  void load(const std::string &path) {
    config_path_ = path;

    if (!std::filesystem::exists(path)) {
      DEMU_ERROR("Config file not found: {}", path);
      return;
    }

    json root;
    try {
      std::ifstream f(path);
      if (!f.is_open()) {
        DEMU_ERROR("Failed to open config file: {}", path);
        return;
      }
      f >> root;
    } catch (const std::exception &e) {
      DEMU_ERROR("Failed to parse config JSON: {}", e.what());
      return;
    }

    parse_isa(root);
    parse_cache(root);
    parse_cpu(root);
    parse_bus(root);
    valid_ = true;

    DEMU_INFO("Config loaded: {}", path);
  }

  static addr_t parse_addr(const json &j) {
    if (j.is_number())
      return j.get<addr_t>();
    return static_cast<addr_t>(std::stoull(j.get<std::string>(), nullptr, 0));
  }

  void parse_isa(const json &root) {
    if (!root.contains("isa"))
      return;
    const auto &j = root["isa"];
    isa_.isa_name = j.value("ISA", isa_.isa_name);
    isa_.xlen = j.value("XLen", isa_.xlen);
    isa_.ilen = j.value("ILen", isa_.ilen);
    isa_.num_arch_regs = j.value("NumArchRegs", isa_.num_arch_regs);
    isa_.is_big_endian = j.value("IsBigEndian", isa_.is_big_endian);
    isa_.is_debug = j.value("IsDebug", isa_.is_debug);
    if (j.contains("Bubble"))
      isa_.bubble = j["Bubble"].get<std::string>();
  }

  void parse_cache(const json &root) {
    if (!root.contains("cache"))
      return;
    const auto &c = root["cache"];
    if (c.contains("l1i")) {
      const auto &j = c["l1i"];
      l1i_.sets = j.value("L1ICacheSets", l1i_.sets);
      l1i_.ways = j.value("L1ICacheWays", l1i_.ways);
      l1i_.line_size = j.value("L1ICacheLineSize", l1i_.line_size);
      if (j.contains("L1ICacheReplPolicy"))
        l1i_.repl_policy =
            parseReplPolicy(j["L1ICacheReplPolicy"].get<std::string>());
    }
    if (c.contains("l1d")) {
      const auto &j = c["l1d"];
      l1d_.sets = j.value("L1DCacheSets", l1d_.sets);
      l1d_.ways = j.value("L1DCacheWays", l1d_.ways);
      l1d_.line_size = j.value("L1DCacheLineSize", l1d_.line_size);
      if (j.contains("L1DCacheReplPolicy"))
        l1d_.repl_policy =
            parseReplPolicy(j["L1DCacheReplPolicy"].get<std::string>());
    }
  }

  void parse_cpu(const json &root) {
    if (!root.contains("cpu"))
      return;
    const auto &j = root["cpu"];
    cpu_.num_phy_regs = j.value("NumPhyRegs", cpu_.num_phy_regs);
    cpu_.ibuffer_size = j.value("IBufferSize", cpu_.ibuffer_size);
    cpu_.rob_size = j.value("ROBSize", cpu_.rob_size);
    cpu_.use_bypass = j.value("IsRegfileUseBypass", cpu_.use_bypass);
  }

  void parse_bus(const json &root) {
    if (!root.contains("bus"))
      return;
    const auto &j = root["bus"];
    if (j.contains("BusType"))
      bus_.type = parseBusType(j["BusType"].get<std::string>());
    bus_.crossbar_fifo_depth =
        j.value("BusCrossbarFifoDepthPerClient", bus_.crossbar_fifo_depth);
    if (j.contains("BusAddressMap")) {
      for (const auto &entry : j["BusAddressMap"]) {
        MemRegion r;
        r.device = entry.value("device", "");
        r.type = entry.value("type", "memory");
        r.base = entry.contains("base") ? parse_addr(entry["base"]) : 0;
        r.size = entry.contains("size") ? parse_addr(entry["size"]) : 0;
        bus_.address_map.push_back(std::move(r));
      }
    }
  }
};

} // namespace demu
