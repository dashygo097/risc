#pragma once

#include "./isa/isa.hh"
#include "./logger.hh"
#include "risc_config.pb.h"
#include <filesystem>
#include <fstream>
#include <google/protobuf/util/json_util.h>
#include <sstream>
#include <string>

namespace demu {
using namespace isa;

class RiscConfig {
public:
  RiscConfig() { load(get_config_path()); }
  explicit RiscConfig(const std::string &p) { load(p); }

  [[nodiscard]] const risc::CacheConfig &l1i() const noexcept {
    return proto_.l1i();
  }
  [[nodiscard]] const risc::CacheConfig &l1d() const noexcept {
    return proto_.l1d();
  }
  [[nodiscard]] const risc::CpuConfig &cpu() const noexcept {
    return proto_.cpu();
  }
  [[nodiscard]] const risc::BusConfig &bus() const noexcept {
    return proto_.bus();
  }

  [[nodiscard]] const risc::DeviceDescriptor *imem() const noexcept {
    return find_region("imem");
  }
  [[nodiscard]] const risc::DeviceDescriptor *dmem() const noexcept {
    return find_region("dmem");
  }

  [[nodiscard]] bool is_valid() const noexcept { return valid_; }

  [[nodiscard]] uint32_t
  l1i_line_words(uint32_t word_bytes = 4) const noexcept {
    return proto_.l1i().line_size() / word_bytes;
  }
  [[nodiscard]] uint32_t
  l1d_line_words(uint32_t word_bytes = 4) const noexcept {
    return proto_.l1d().line_size() / word_bytes;
  }

  bool validate() const {
    bool ok = true;
    if (!imem()) {
      DEMU_ERROR("RiscConfig: imem not found");
      ok = false;
    }
    if (!dmem()) {
      DEMU_ERROR("RiscConfig: dmem not found");
      ok = false;
    }
    if (proto_.bus().type() == risc::BUS_TYPE_UNKNOWN) {
      DEMU_ERROR("RiscConfig: unknown BusType");
      ok = false;
    }
    return ok;
  }

  void dump() const {
    DEMU_INFO("=== RiscConfig ===");
    DEMU_INFO("  config: {}", config_path_);
    std::string json;
    (void)google::protobuf::util::MessageToJsonString(proto_, &json);
    std::istringstream ss(json);
    std::string line;
    while (std::getline(ss, line))
      DEMU_TRACE("  {}", line);
    for (const auto &r : proto_.bus().address_map())
      DEMU_INFO("    {:6s} base=0x{:08x} size=0x{:x}", r.device(), r.base(),
                r.size());
    DEMU_INFO("==================");
  }

private:
  risc::RiscConfig proto_;
  std::string config_path_;
  bool valid_{false};

  static std::string get_config_path() {
#ifdef RTL_CONFIG_FILE
    return std::string(RTL_CONFIG_FILE);
#else
    DEMU_ERROR("RTL_CONFIG_FILE not defined");
    return "";
#endif
  }

  void load(const std::string &path) {
    config_path_ = path;
    if (!std::filesystem::exists(path)) {
      DEMU_ERROR("Config not found: {}", path);
      return;
    }

    if (path.size() >= 3 && path.substr(path.size() - 3) == ".pb") {
      std::ifstream f(path, std::ios::binary);
      if (!proto_.ParseFromIstream(&f)) {
        DEMU_ERROR("Failed to parse binary protobuf: {}", path);
        return;
      }
    } else {
      std::ifstream f(path);
      std::string json((std::istreambuf_iterator<char>(f)),
                       std::istreambuf_iterator<char>());
      auto status = google::protobuf::util::JsonStringToMessage(json, &proto_);
      if (!status.ok()) {
        DEMU_ERROR("Failed to parse JSON protobuf: {}", status.ToString());
        return;
      }
    }

    valid_ = true;
    DEMU_INFO("Config loaded: {}", path);
  }

  [[nodiscard]] const risc::DeviceDescriptor *
  find_region(const std::string &dev) const noexcept {
    for (const auto &r : proto_.bus().address_map())
      if (r.device() == dev)
        return &r;
    return nullptr;
  }
};

} // namespace demu
