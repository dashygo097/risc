#include "demu/hal/device_manager.hh"
#include <algorithm>
#include <cstdio>

namespace demu::hal {

EmulatedHardware *DeviceManager::get_slave(uint8_t port) noexcept {
  if (port >= _slaves.size()) {
    return nullptr;
  }
  return _slaves[port].get();
}

const EmulatedHardware *DeviceManager::get_slave(uint8_t port) const noexcept {
  if (port >= _slaves.size()) {
    return nullptr;
  }
  return _slaves[port].get();
}

EmulatedHardware *DeviceManager::find_slave_for_address(addr_t addr) noexcept {
  for (const auto &slave : _slaves) {
    if (slave && slave->owns_address(addr)) {
      return slave.get();
    }
  }
  return nullptr;
}

const EmulatedHardware *
DeviceManager::find_slave_for_address(addr_t addr) const noexcept {
  for (const auto &slave : _slaves) {
    if (slave && slave->owns_address(addr)) {
      return slave.get();
    }
  }
  return nullptr;
}

void DeviceManager::reset() noexcept {
  for (auto &slave : _slaves) {
    if (slave) {
      slave->reset();
    }
  }
}

void DeviceManager::clock_tick() noexcept {
  for (auto &slave : _slaves) {
    if (slave) {
      slave->clock_tick();
    }
  }
}

size_t DeviceManager::active_slave_count() const noexcept {
  return std::count_if(_slaves.begin(), _slaves.end(),
                       [](const auto &slave) { return slave != nullptr; });
}

bool DeviceManager::has_slave_at(uint8_t port) const noexcept {
  return port < _slaves.size() && _slaves[port] != nullptr;
}

std::optional<std::string_view>
DeviceManager::get_slave_name(uint8_t port) const noexcept {
  if (!has_slave_at(port)) {
    return std::nullopt;
  }
  return _slave_names[port];
}

void DeviceManager::dump_device_map() const {
  std::printf("Active Slaves: %zu / %zu ports\n\n", active_slave_count(),
              port_count());

  for (size_t i = 0; i < _slaves.size(); ++i) {
    if (!_slaves[i]) {
      continue;
    }

    const addr_t base = _slaves[i]->base_address();
    const addr_t end = base + _slaves[i]->address_range() - 1;

    std::printf("Port %1zu: %-20s [0x%08X - 0x%08X] (%zu bytes)\n", i,
                _slave_names[i].c_str(), static_cast<unsigned>(base),
                static_cast<unsigned>(end), _slaves[i]->address_range());
  }
}

void DeviceManager::ensure_capacity(uint8_t port) {
  if (port >= _slaves.size()) {
    _slaves.resize(port + 1);
    _slave_names.resize(port + 1);
  }
}

} // namespace demu::hal
