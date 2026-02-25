#pragma once

#include "../logger.hh"
#include "./emu.hh"
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

namespace demu::hal {

class DeviceManager final {
public:
  DeviceManager() = default;
  ~DeviceManager() = default;

  // Slave Registration
  template <typename T, typename... Args>
  T *register_slave(uint8_t port, std::string_view name, Args &&...args);

  // Slave Retrieval
  [[nodiscard]] EmulatedHardware *get_slave(uint8_t port) noexcept;
  [[nodiscard]] const EmulatedHardware *get_slave(uint8_t port) const noexcept;

  template <typename T> [[nodiscard]] T *get_slave(uint8_t port) noexcept;

  template <typename T>
  [[nodiscard]] const T *get_slave(uint8_t port) const noexcept;

  [[nodiscard]] EmulatedHardware *find_slave_for_address(addr_t addr) noexcept;
  [[nodiscard]] const EmulatedHardware *
  find_slave_for_address(addr_t addr) const noexcept;

  // Operations
  void reset() noexcept;
  void clock_tick() noexcept;

  // Information
  [[nodiscard]] size_t port_count() const noexcept { return _slaves.size(); }
  [[nodiscard]] size_t active_slave_count() const noexcept;
  [[nodiscard]] bool has_slave_at(uint8_t port) const noexcept;
  [[nodiscard]] std::optional<std::string_view>
  get_slave_name(uint8_t port) const noexcept;

  void dump_memory(addr_t start, size_t size) const;
  void dump_device_map() const;

private:
  void ensure_capacity(uint8_t port);

  std::vector<std::unique_ptr<EmulatedHardware>> _slaves;
  std::vector<std::string> _slave_names;
};

// Template Implementation
template <typename T, typename... Args>
T *DeviceManager::register_slave(uint8_t port, std::string_view name,
                                 Args &&...args) {
  static_assert(std::is_base_of_v<EmulatedHardware, T>,
                "T must derive from EmulatedHardware");

  ensure_capacity(port);

  try {
    auto slave = std::make_unique<T>(std::forward<Args>(args)...);
    T *ptr = slave.get();
    _slaves[port] = std::move(slave);
    _slave_names[port] = std::string(name);

    HAL_INFO("Registered device '{}' on Port {} [Base: 0x{:08X}]", name, port,
             ptr->base_address());

    return ptr;
  } catch (const std::exception &e) {
    HAL_ERROR("Failed to create slave '{}' on Port {}: {}", name, port,
              e.what());
    return nullptr;
  }
}

template <typename T> T *DeviceManager::get_slave(uint8_t port) noexcept {
  static_assert(std::is_base_of_v<EmulatedHardware, T>,
                "T must derive from EmulatedHardware");
  return dynamic_cast<T *>(get_slave(port));
}

template <typename T>
const T *DeviceManager::get_slave(uint8_t port) const noexcept {
  static_assert(std::is_base_of_v<EmulatedHardware, T>,
                "T must derive from EmulatedHardware");
  return dynamic_cast<const T *>(get_slave(port));
}

} // namespace demu::hal
