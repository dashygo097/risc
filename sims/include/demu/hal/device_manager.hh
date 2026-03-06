#pragma once

#include "../logger.hh"
#include "./hardware.hh"
#include "./port_handler.hh"
#include <cstdint>
#include <map>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

namespace demu::hal {

using port_id_t = uint8_t;

class DeviceManager final {
public:
  DeviceManager() = default;
  ~DeviceManager() = default;

  // Non-copyable, movable
  DeviceManager(const DeviceManager &) = delete;
  DeviceManager &operator=(const DeviceManager &) = delete;
  DeviceManager(DeviceManager &&) = default;
  DeviceManager &operator=(DeviceManager &&) = default;

  // Slave Registration
  template <typename T, typename... Args>
  T *register_slave(port_id_t port, std::string_view name, Args &&...args);

  // Slave Retrieval — by port
  [[nodiscard]] Hardware *get_slave(port_id_t port) noexcept;
  [[nodiscard]] const Hardware *get_slave(port_id_t port) const noexcept;

  template <typename T> [[nodiscard]] T *get_slave(port_id_t port) noexcept;

  template <typename T>
  [[nodiscard]] const T *get_slave(port_id_t port) const noexcept;

  // Slave Retrieval — by name
  [[nodiscard]] Hardware *get_slave_by_name(std::string_view name) noexcept;
  [[nodiscard]] const Hardware *
  get_slave_by_name(std::string_view name) const noexcept;

  template <typename T>
  [[nodiscard]] T *get_slave_by_name(std::string_view name) noexcept;

  template <typename T>
  [[nodiscard]] const T *
  get_slave_by_name(std::string_view name) const noexcept;

  // Slave Retrieval — by address
  [[nodiscard]] Hardware *find_slave_for_address(addr_t addr) noexcept;
  [[nodiscard]] const Hardware *
  find_slave_for_address(addr_t addr) const noexcept;

  // Port Handlers
  void register_handler(port_id_t port, std::unique_ptr<PortHandler> handler);
  void handle_ports() noexcept;

  // Bulk Operations
  void reset() noexcept;
  void clock_tick() noexcept;

  // Informational
  [[nodiscard]] size_t port_count() const noexcept { return slots_.size(); }
  [[nodiscard]] size_t active_slave_count() const noexcept {
    return name_indices_.size();
  }
  [[nodiscard]] bool has_slave_at(port_id_t port) const noexcept;
  [[nodiscard]] std::optional<std::string_view>
  get_slave_name(port_id_t port) const noexcept;

  void dump_device_map() const;

private:
  struct SlaveSlot {
    std::unique_ptr<Hardware> device;
    std::string name;
    std::unique_ptr<PortHandler> handler;
  };

  // components
  std::vector<SlaveSlot> slots_;
  std::unordered_map<std::string, port_id_t> name_indices_;
  std::map<addr_t, port_id_t> addr_indices_;

  // helpers
  void ensure_capacity(port_id_t port);
  void rebuild_indices_for(port_id_t port);
  void remove_indices_for(port_id_t port);
};

template <typename T, typename... Args>
T *DeviceManager::register_slave(port_id_t port, std::string_view name,
                                 Args &&...args) {
  static_assert(std::is_base_of_v<Hardware, T>, "T must derive from Hardware");

  ensure_capacity(port);

  if (slots_[port].device) {
    remove_indices_for(port);
  }

  try {
    auto slave = std::make_unique<T>(std::forward<Args>(args)...);
    T *ptr = slave.get();

    slots_[port].device = std::move(slave);
    slots_[port].name = std::string(name);

    rebuild_indices_for(port);

    HAL_INFO("Registered device '{}' on Port {} [Base: 0x{:08X}]", name, port,
             static_cast<uint32_t>(ptr->base_address()));

    return ptr;
  } catch (const std::exception &e) {
    HAL_ERROR("Failed to create slave '{}' on Port {}: {}", name, port,
              e.what());
    return nullptr;
  }
}

template <typename T> T *DeviceManager::get_slave(port_id_t port) noexcept {
  static_assert(std::is_base_of_v<Hardware, T>, "T must derive from Hardware");
  return dynamic_cast<T *>(get_slave(port));
}

template <typename T>
const T *DeviceManager::get_slave(port_id_t port) const noexcept {
  static_assert(std::is_base_of_v<Hardware, T>, "T must derive from Hardware");
  return dynamic_cast<const T *>(get_slave(port));
}

template <typename T>
T *DeviceManager::get_slave_by_name(std::string_view name) noexcept {
  static_assert(std::is_base_of_v<Hardware, T>, "T must derive from Hardware");
  return dynamic_cast<T *>(get_slave_by_name(name));
}

template <typename T>
const T *
DeviceManager::get_slave_by_name(std::string_view name) const noexcept {
  static_assert(std::is_base_of_v<Hardware, T>, "T must derive from Hardware");
  return dynamic_cast<const T *>(get_slave_by_name(name));
}

} // namespace demu::hal
