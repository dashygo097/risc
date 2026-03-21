#include "demu/hal/device_manager.hh"
#include "demu/logger.hh"

namespace demu::hal {

// Device Retrieval — by port
Device *DeviceManager::get_device(port_id_t port) noexcept {
  if (port >= slots_.size())
    return nullptr;
  return slots_[port].device.get();
}

const Device *DeviceManager::get_device(port_id_t port) const noexcept {
  if (port >= slots_.size())
    return nullptr;
  return slots_[port].device.get();
}

// Device Retrieval — by name
Device *DeviceManager::get_device_by_name(std::string_view name) noexcept {
  auto it = name_indices_.find(std::string(name));
  if (it == name_indices_.end())
    return nullptr;
  return get_device(it->second);
}

const Device *
DeviceManager::get_device_by_name(std::string_view name) const noexcept {
  auto it = name_indices_.find(std::string(name));
  if (it == name_indices_.end())
    return nullptr;
  return get_device(it->second);
}

// Device Retrieval — by address
Device *DeviceManager::find_device_for_address(addr_t addr) noexcept {
  if (addr_indices_.empty())
    return nullptr;

  auto it = addr_indices_.upper_bound(addr);
  if (it == addr_indices_.begin())
    return nullptr;

  --it;
  auto *device = get_device(it->second);
  if (device && device->owns_address(addr)) {
    HAL_TRACE("Address 0x{:08X} mapped to device '{}' (Port {})", addr,
              slots_[it->second].desc.name(), it->second);
    return device;
  }

  HAL_WARN("No device device owns address 0x{:08X}", addr);
  return nullptr;
}

const Device *
DeviceManager::find_device_for_address(addr_t addr) const noexcept {
  if (addr_indices_.empty())
    return nullptr;

  auto it = addr_indices_.upper_bound(addr);
  if (it == addr_indices_.begin())
    return nullptr;

  --it;
  const auto *device = get_device(it->second);
  if (device && device->owns_address(addr)) {
    HAL_TRACE("Address 0x{:08X} mapped to device '{}' (Port {})", addr,
              slots_[it->second].desc.name(), it->second);
    return device;
  }
  return nullptr;
}

// Port Handlers
void DeviceManager::register_handler(port_id_t port,
                                     std::unique_ptr<PortHandler> handler) {
  ensure_capacity(port);
  slots_[port].handler = std::move(handler);
  HAL_DEBUG("Registered '{}' handler on Port {}",
            slots_[port].handler->protocol_name(), port);
}

void DeviceManager::handle_ports() noexcept {
  for (auto &slot : slots_) {
    if (slot.device && slot.handler)
      slot.handler->handle(slot.device.get());
  }
}

// Bulk Operations
void DeviceManager::reset() noexcept {
  HAL_DEBUG("Resetting all emulated hardware devices...");
  for (auto &slot : slots_) {
    if (slot.device)
      slot.device->reset();
  }
}

void DeviceManager::clock_tick() noexcept {
  for (auto &slot : slots_) {
    if (slot.device)
      slot.device->clock_tick();
  }
}

// Informational
bool DeviceManager::has_device_at(port_id_t port) const noexcept {
  return port < slots_.size() && slots_[port].device != nullptr;
}

std::optional<std::string_view>
DeviceManager::get_device_name(port_id_t port) const noexcept {
  if (!has_device_at(port))
    return std::nullopt;
  return std::string_view(slots_[port].desc.name());
}

void DeviceManager::dump_device_map() const {
  HAL_INFO("--- Device Map Summary ---");
  HAL_INFO("Active Devices: {} / {} ports", active_device_count(),
           port_count());

  for (const auto &[base, port] : addr_indices_) {
    const auto &slot = slots_[port];
    const addr_t range = slot.device->address_range();
    const addr_t end = base + range - 1;

    HAL_INFO("Port {:>2}: {:<20} [0x{:08X} - 0x{:08X}] ({:>8} bytes)", port,
             slot.desc.name(), static_cast<uint32_t>(base),
             static_cast<uint32_t>(end), static_cast<uint32_t>(range));
  }
}

void DeviceManager::ensure_capacity(port_id_t port) {
  if (port >= slots_.size()) {
    slots_.resize(static_cast<size_t>(port) + 1);
  }
}

void DeviceManager::rebuild_indices_for(port_id_t port) {
  auto &slot = slots_[port];
  if (!slot.device)
    return;

  name_indices_[slot.desc.name()] = port;
  addr_indices_[slot.device->base_address()] = port;
}

void DeviceManager::remove_indices_for(port_id_t port) {
  auto &slot = slots_[port];
  if (!slot.device)
    return;

  name_indices_.erase(slot.desc.name());
  addr_indices_.erase(slot.device->base_address());
}

} // namespace demu::hal
