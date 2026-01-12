#include "demu/hal/axi/manager.hh"
#include <algorithm>
#include <iostream>

namespace demu::hal {

AXISlave *AXIBusManager::get_slave(uint8_t port) noexcept {
  if (port >= slaves_.size()) {
    return nullptr;
  }
  return slaves_[port].get();
}

const AXISlave *AXIBusManager::get_slave(uint8_t port) const noexcept {
  if (port >= slaves_.size()) {
    return nullptr;
  }
  return slaves_[port].get();
}

AXISlave *AXIBusManager::find_slave_for_address(addr_t addr) noexcept {
  for (const auto &slave : slaves_) {
    if (slave && slave->owns_address(addr)) {
      return slave.get();
    }
  }
  return nullptr;
}

const AXISlave *
AXIBusManager::find_slave_for_address(addr_t addr) const noexcept {
  for (const auto &slave : slaves_) {
    if (slave && slave->owns_address(addr)) {
      return slave.get();
    }
  }
  return nullptr;
}

void AXIBusManager::reset() noexcept {
  for (auto &slave : slaves_) {
    if (slave) {
      slave->reset();
    }
  }
}

void AXIBusManager::clock_tick() noexcept {
  for (auto &slave : slaves_) {
    if (slave) {
      slave->clock_tick();
    }
  }
}

size_t AXIBusManager::active_slave_count() const noexcept {
  return std::count_if(slaves_.begin(), slaves_.end(),
                       [](const auto &slave) { return slave != nullptr; });
}

bool AXIBusManager::has_slave_at(uint8_t port) const noexcept {
  return port < slaves_.size() && slaves_[port] != nullptr;
}

std::optional<std::string_view>
AXIBusManager::get_slave_name(uint8_t port) const noexcept {
  if (!has_slave_at(port)) {
    return std::nullopt;
  }
  return slave_names_[port];
}

void AXIBusManager::dump_device_map() const {
  for (size_t i = 0; i < slaves_.size(); ++i) {
    if (!slaves_[i]) {
      continue;
    }

    const addr_t base = slaves_[i]->base_address();
    const addr_t end = base + slaves_[i]->size() - 1;

    std::printf("M_AXI_%zu: %-20s [0x%08X - 0x%08X] (%zu bytes)\n", i,
                slave_names_[i].c_str(), static_cast<unsigned>(base),
                static_cast<unsigned>(end), slaves_[i]->size());
  }
}

void AXIBusManager::ensure_capacity(uint8_t port) {
  if (port >= slaves_.size()) {
    slaves_.resize(port + 1);
    slave_names_.resize(port + 1);
  }
}

} // namespace demu::hal
