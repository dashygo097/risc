#include "demu/hal/device_manager.hh"
#include "demu/logger.hh"

namespace demu::hal {

// Slave Retrieval — by port
EmulatedHardware *DeviceManager::get_slave(port_id_t port) noexcept {
  if (port >= _slots.size())
    return nullptr;
  return _slots[port].device.get();
}

const EmulatedHardware *
DeviceManager::get_slave(port_id_t port) const noexcept {
  if (port >= _slots.size())
    return nullptr;
  return _slots[port].device.get();
}

// Slave Retrieval — by name
EmulatedHardware *
DeviceManager::get_slave_by_name(std::string_view name) noexcept {
  auto it = _name_index.find(std::string(name));
  if (it == _name_index.end())
    return nullptr;
  return get_slave(it->second);
}

const EmulatedHardware *
DeviceManager::get_slave_by_name(std::string_view name) const noexcept {
  auto it = _name_index.find(std::string(name));
  if (it == _name_index.end())
    return nullptr;
  return get_slave(it->second);
}

// Slave Retrieval — by address
EmulatedHardware *DeviceManager::find_slave_for_address(addr_t addr) noexcept {
  if (_addr_index.empty())
    return nullptr;

  auto it = _addr_index.upper_bound(addr);
  if (it == _addr_index.begin())
    return nullptr;

  --it;
  auto *slave = get_slave(it->second);
  if (slave && slave->owns_address(addr)) {
    HAL_TRACE("Address 0x{:08X} mapped to slave '{}' (Port {})", addr,
              _slots[it->second].name, it->second);
    return slave;
  }

  HAL_WARN("No slave device owns address 0x{:08X}", addr);
  return nullptr;
}

const EmulatedHardware *
DeviceManager::find_slave_for_address(addr_t addr) const noexcept {
  if (_addr_index.empty())
    return nullptr;

  auto it = _addr_index.upper_bound(addr);
  if (it == _addr_index.begin())
    return nullptr;

  --it;
  const auto *slave = get_slave(it->second);
  if (slave && slave->owns_address(addr)) {
    HAL_TRACE("Address 0x{:08X} mapped to slave '{}' (Port {})", addr,
              _slots[it->second].name, it->second);
    return slave;
  }
  return nullptr;
}

// Bulk Operations
void DeviceManager::reset() noexcept {
  HAL_DEBUG("Resetting all emulated hardware devices...");
  for (auto &slot : _slots) {
    if (slot.device)
      slot.device->reset();
  }
}

void DeviceManager::clock_tick() noexcept {
  for (auto &slot : _slots) {
    if (slot.device)
      slot.device->clock_tick();
  }
}

// Informational
bool DeviceManager::has_slave_at(port_id_t port) const noexcept {
  return port < _slots.size() && _slots[port].device != nullptr;
}

std::optional<std::string_view>
DeviceManager::get_slave_name(port_id_t port) const noexcept {
  if (!has_slave_at(port))
    return std::nullopt;
  return std::string_view(_slots[port].name);
}

void DeviceManager::dump_device_map() const {
  HAL_INFO("--- Device Map Summary ---");
  HAL_INFO("Active Slaves: {} / {} ports", active_slave_count(), port_count());

  for (const auto &[base, port] : _addr_index) {
    const auto &slot = _slots[port];
    const addr_t range = slot.device->address_range();
    const addr_t end = base + range - 1;

    HAL_INFO("Port {:>2}: {:<20} [0x{:08X} - 0x{:08X}] ({:>8} bytes)", port,
             slot.name, static_cast<uint32_t>(base), static_cast<uint32_t>(end),
             static_cast<uint32_t>(range));
  }
}

void DeviceManager::ensure_capacity(port_id_t port) {
  if (port >= _slots.size()) {
    _slots.resize(static_cast<size_t>(port) + 1);
  }
}

void DeviceManager::rebuild_indices_for(port_id_t port) {
  auto &slot = _slots[port];
  if (!slot.device)
    return;

  _name_index[slot.name] = port;
  _addr_index[slot.device->base_address()] = port;
}

void DeviceManager::remove_indices_for(port_id_t port) {
  auto &slot = _slots[port];
  if (!slot.device)
    return;

  _name_index.erase(slot.name);
  _addr_index.erase(slot.device->base_address());
}

} // namespace demu::hal
