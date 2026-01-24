#pragma once

#include "./slave.hh"
#include <cstddef>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

namespace demu::hal::axi {

class AXILiteBusManager final {
public:
  AXILiteBusManager() = default;
  ~AXILiteBusManager() = default;

  // Slave Registration
  template <typename T, typename... Args>
  T *register_slave(uint8_t port, std::string_view name, Args &&...args);

  // Slave Retrieval
  [[nodiscard]] AXILiteSlave *get_slave(uint8_t port) noexcept;
  [[nodiscard]] const AXILiteSlave *get_slave(uint8_t port) const noexcept;
  template <typename T> [[nodiscard]] T *get_slave(uint8_t port) noexcept;
  template <typename T>
  [[nodiscard]] const T *get_slave(uint8_t port) const noexcept;
  [[nodiscard]] AXILiteSlave *find_slave_for_address(addr_t addr) noexcept;
  [[nodiscard]] const AXILiteSlave *
  find_slave_for_address(addr_t addr) const noexcept;

  // Operations
  void reset() noexcept;
  void clock_tick() noexcept;

  // Information
  [[nodiscard]] size_t port_count() const noexcept { return slaves_.size(); }
  [[nodiscard]] size_t active_slave_count() const noexcept;
  [[nodiscard]] bool has_slave_at(uint8_t port) const noexcept;
  [[nodiscard]] std::optional<std::string_view>
  get_slave_name(uint8_t port) const noexcept;
  void dump_device_map() const;

private:
  void ensure_capacity(uint8_t port);

  std::vector<std::unique_ptr<AXILiteSlave>> slaves_;
  std::vector<std::string> slave_names_;
};

template <typename T, typename... Args>
T *AXILiteBusManager::register_slave(uint8_t port, std::string_view name,
                                     Args &&...args) {
  static_assert(std::is_base_of_v<AXILiteSlave, T>,
                "T must derive from AXILiteSlave");

  ensure_capacity(port);

  try {
    auto slave = std::make_unique<T>(std::forward<Args>(args)...);
    T *ptr = slave.get();

    slaves_[port] = std::move(slave);
    slave_names_[port] = std::string(name);

    return ptr;
  } catch (const std::exception &e) {
    std::cerr << "Failed to create slave '" << name << "': " << e.what()
              << '\n';
    return nullptr;
  }
}

template <typename T> T *AXILiteBusManager::get_slave(uint8_t port) noexcept {
  static_assert(std::is_base_of_v<AXILiteSlave, T>,
                "T must derive from AXILiteSlave");
  return dynamic_cast<T *>(get_slave(port));
}

template <typename T>
const T *AXILiteBusManager::get_slave(uint8_t port) const noexcept {
  static_assert(std::is_base_of_v<AXILiteSlave, T>,
                "T must derive from AXILiteSlave");
  return dynamic_cast<const T *>(get_slave(port));
}

} // namespace demu::hal::axi
