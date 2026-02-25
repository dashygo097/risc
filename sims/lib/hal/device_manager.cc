#include "demu/hal/device_manager.hh"
#include "demu/hal/axilite/memory.hh"
#include "demu/logger.hh"
#include <algorithm>
#include <iomanip>

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
  for (size_t i = 0; i < _slaves.size(); ++i) {
    if (_slaves[i] && _slaves[i]->owns_address(addr)) {
      HAL_TRACE("Address 0x{:08X} mapped to slave '{}' (Port {})", addr,
                _slave_names[i], i);
      return _slaves[i].get();
    }
  }
  HAL_WARN("No slave device owns address 0x{:08X}", addr);
  return nullptr;
}

const EmulatedHardware *
DeviceManager::find_slave_for_address(addr_t addr) const noexcept {
  for (size_t i = 0; i < _slaves.size(); ++i) {
    if (_slaves[i] && _slaves[i]->owns_address(addr)) {
      HAL_TRACE("Address 0x{:08X} mapped to slave '{}' (Port {})", addr,
                _slave_names[i], i);
      return _slaves[i].get();
    }
  }
  return nullptr;
}

void DeviceManager::reset() noexcept {
  HAL_DEBUG("Resetting all emulated hardware devices...");
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
  return std::string_view(_slave_names[port]);
}

void DeviceManager::dump_memory(addr_t start, size_t size) const {
  auto *slave = find_slave_for_address(start);
  if (!slave) {
    return;
  }

  auto *mem = dynamic_cast<const hal::axi::AXILiteMemory *>(slave);
  if (mem) {
    HAL_INFO("Memory dump [0x{:08X} - 0x{:08X}]:", static_cast<uint64_t>(start),
             static_cast<uint64_t>(start + size));

    const byte_t *ptr = mem->get_ptr(start);
    if (ptr) {
      addr_t offset = start - mem->base_address();
      for (size_t i = 0; i < size && (offset + i) < mem->address_range();
           i += 16) {
        std::stringstream ss;
        ss << std::hex << std::setw(8) << std::setfill('0') << (start + i)
           << ": ";

        for (size_t j = 0; j < 16; j++) {
          if (i + j < size) {
            ss << std::hex << std::setw(2) << std::setfill('0')
               << (int)ptr[i + j] << " ";
          } else {
            ss << "   ";
          }
          if (j == 7)
            ss << " ";
        }

        ss << " |";
        for (size_t j = 0; j < 16 && (i + j) < size; j++) {
          byte_t c = ptr[i + j];
          ss << (char)((c >= 32 && c < 127) ? c : '.');
        }
        ss << "|";

        HAL_INFO("{}", ss.str());
      }
    }
  }
}

void DeviceManager::dump_device_map() const {
  HAL_INFO("--- Device Map Summary ---");
  HAL_INFO("Active Slaves: {} / {} ports", active_slave_count(), port_count());

  for (size_t i = 0; i < _slaves.size(); ++i) {
    if (!_slaves[i]) {
      continue;
    }

    const addr_t base = _slaves[i]->base_address();
    const addr_t range = _slaves[i]->address_range();
    const addr_t end = base + range - 1;

    HAL_INFO("Port {:>2}: {:<20} [0x{:08X} - 0x{:08X}] ({:>8} bytes)", i,
             _slave_names[i], static_cast<uint32_t>(base),
             static_cast<uint32_t>(end), static_cast<uint32_t>(range));
  }
}

void DeviceManager::ensure_capacity(uint8_t port) {
  if (port >= _slaves.size()) {
    _slaves.resize(port + 1);
    _slave_names.resize(port + 1);
  }
}

} // namespace demu::hal
