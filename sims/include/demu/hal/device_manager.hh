#pragma once

#include "../logger.hh"
#include "./device.hh"
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

  // Device Registration
  template <typename T, typename... Args>
  T *register_device(port_id_t port, risc::DeviceDescriptor desc,
                     Args &&...args);

  // Device Retrieval — by port
  [[nodiscard]] Device *get_device(port_id_t port) noexcept;
  [[nodiscard]] const Device *get_device(port_id_t port) const noexcept;

  template <typename T> [[nodiscard]] T *get_device(port_id_t port) noexcept;

  template <typename T>
  [[nodiscard]] const T *get_device(port_id_t port) const noexcept;

  // Device Retrieval — by name
  [[nodiscard]] Device *get_device_by_name(std::string_view name) noexcept;
  [[nodiscard]] const Device *
  get_device_by_name(std::string_view name) const noexcept;

  template <typename T>
  [[nodiscard]] T *get_device_by_name(std::string_view name) noexcept;

  template <typename T>
  [[nodiscard]] const T *
  get_device_by_name(std::string_view name) const noexcept;

  // Device Retrieval — by address
  [[nodiscard]] Device *find_device_for_address(addr_t addr) noexcept;
  [[nodiscard]] const Device *
  find_device_for_address(addr_t addr) const noexcept;

  // Port Handlers
  void register_handler(port_id_t port, std::unique_ptr<PortHandler> handler);
  void handle_ports() noexcept;

  // Bulk Operations
  void reset() noexcept;
  void clock_tick() noexcept;

  // Informational
  [[nodiscard]] size_t port_count() const noexcept { return slots_.size(); }
  [[nodiscard]] size_t active_device_count() const noexcept {
    return name_indices_.size();
  }
  [[nodiscard]] bool has_device_at(port_id_t port) const noexcept;
  [[nodiscard]] std::optional<std::string_view>
  get_device_name(port_id_t port) const noexcept;

  void dump_device_map() const;

private:
  struct DeviceSlot {
    risc::DeviceDescriptor desc;
    std::unique_ptr<Device> device;
    std::unique_ptr<PortHandler> handler;
  };

  // components
  std::vector<DeviceSlot> slots_;
  std::unordered_map<std::string, port_id_t> name_indices_;
  std::map<addr_t, port_id_t> addr_indices_;

  // helpers
  void ensure_capacity(port_id_t port);
  void rebuild_indices_for(port_id_t port);
  void remove_indices_for(port_id_t port);
};

template <typename T, typename... Args>
T *DeviceManager::register_device(port_id_t port, risc::DeviceDescriptor desc,
                                  Args &&...args) {
  static_assert(std::is_base_of_v<Device, T>, "T must derive from Device");

  ensure_capacity(port);

  if (slots_[port].device) {
    remove_indices_for(port);
  }

  try {
    auto device = std::make_unique<T>(desc, std::forward<Args>(args)...);
    T *ptr = device.get();

    slots_[port].desc = desc;
    slots_[port].device = std::move(device);

    rebuild_indices_for(port);

    HAL_DEBUG("Registered device '{}' on Port {} [Base: 0x{:08X}]", desc.name(),
              port, static_cast<uint32_t>(ptr->base_address()));

    return ptr;
  } catch (const std::exception &e) {
    HAL_ERROR("Failed to create device '{}' on Port {}: {}", desc.name(), port,
              e.what());
    return nullptr;
  }
}

template <typename T> T *DeviceManager::get_device(port_id_t port) noexcept {
  static_assert(std::is_base_of_v<Device, T>, "T must derive from Device");
  return dynamic_cast<T *>(get_device(port));
}

template <typename T>
const T *DeviceManager::get_device(port_id_t port) const noexcept {
  static_assert(std::is_base_of_v<Device, T>, "T must derive from Device");
  return dynamic_cast<const T *>(get_device(port));
}

template <typename T>
T *DeviceManager::get_device_by_name(std::string_view name) noexcept {
  static_assert(std::is_base_of_v<Device, T>, "T must derive from Device");
  return dynamic_cast<T *>(get_device_by_name(name));
}

template <typename T>
const T *
DeviceManager::get_device_by_name(std::string_view name) const noexcept {
  static_assert(std::is_base_of_v<Device, T>, "T must derive from Device");
  return dynamic_cast<const T *>(get_device_by_name(name));
}

} // namespace demu::hal
