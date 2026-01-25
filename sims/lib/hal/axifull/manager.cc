#include "demu/hal/axifull/manager.hh"
#include <algorithm>

namespace demu::hal::axi {

AXIFullSlave *AXIFullBusManager::get_slave(uint8_t port) noexcept {
  if (port >= _slaves.size()) {
    return nullptr;
  }
  return _slaves[port].get();
}

const AXIFullSlave *AXIFullBusManager::get_slave(uint8_t port) const noexcept {
  if (port >= _slaves.size()) {
    return nullptr;
  }
  return _slaves[port].get();
}

AXIFullSlave *AXIFullBusManager::find_slave_for_address(addr_t addr) noexcept {
  for (const auto &slave : _slaves) {
    if (slave && slave->owns_address(addr)) {
      return slave.get();
    }
  }
  return nullptr;
}

const AXIFullSlave *
AXIFullBusManager::find_slave_for_address(addr_t addr) const noexcept {
  for (const auto &slave : _slaves) {
    if (slave && slave->owns_address(addr)) {
      return slave.get();
    }
  }
  return nullptr;
}

void AXIFullBusManager::reset() noexcept {
  for (auto &slave : _slaves) {
    if (slave) {
      slave->reset();
    }
  }
}

void AXIFullBusManager::clock_tick() noexcept {
  for (auto &slave : _slaves) {
    if (slave) {
      slave->clock_tick();
    }
  }
}

size_t AXIFullBusManager::active_slave_count() const noexcept {
  return std::count_if(_slaves.begin(), _slaves.end(),
                       [](const auto &slave) { return slave != nullptr; });
}

bool AXIFullBusManager::has_slave_at(uint8_t port) const noexcept {
  return port < _slaves.size() && _slaves[port] != nullptr;
}

std::optional<std::string_view>
AXIFullBusManager::get_slave_name(uint8_t port) const noexcept {
  if (!has_slave_at(port)) {
    return std::nullopt;
  }
  return _slave_names[port];
}

void AXIFullBusManager::dump_device_map() const {
  for (size_t i = 0; i < _slaves.size(); ++i) {
    if (!_slaves[i]) {
      continue;
    }

    const addr_t base = _slaves[i]->base_address();
    const addr_t end = base + _slaves[i]->address_range() - 1;

    std::printf("M_AXIFull_%zu: %-20s [0x%08X - 0x%08X] (%zu bytes)\n", i,
                _slave_names[i].c_str(), static_cast<unsigned>(base),
                static_cast<unsigned>(end), _slaves[i]->address_range());
  }
}

void AXIFullBusManager::ensure_capacity(uint8_t port) {
  if (port >= _slaves.size()) {
    _slaves.resize(port + 1);
    _slave_names.resize(port + 1);
  }
}

} // namespace demu::hal::axi
