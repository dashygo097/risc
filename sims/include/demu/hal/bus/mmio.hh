#pragma once

#include "../ihardware.hh"
#include <algorithm>
#include <map>
#include <memory>
#include <vector>

namespace demu::hal {

class MMIOBus {
public:
  MMIOBus() = default;
  ~MMIOBus() = default;

  template <typename T, typename... Args>
  T *register_hardware(const std::string &name, Args &&...args) {
    auto hw = std::make_unique<T>(std::forward<Args>(args)...);
    T *ptr = hw.get();
    _devices[name] = std::move(hw);
    return ptr;
  }

  template <typename T> T *get_hardware(const std::string &name) {
    auto it = _devices.find(name);
    if (it == _devices.end()) {
      return nullptr;
    }
    return dynamic_cast<T *>(it->second.get());
  }

  MMIOResult read(addr_t addr, size_t size) {
    IHardware *hw = find_device(addr);
    if (!hw) {
      return MMIOResult::err("No device at address 0x" + std::to_string(addr));
    }
    return hw->read(hw->to_offset(addr), size);
  }

  MMIOResult write(addr_t addr, word_t data, size_t size) {
    IHardware *hw = find_device(addr);
    if (!hw) {
      return MMIOResult::err("No device at address 0x" + std::to_string(addr));
    }
    return hw->write(hw->to_offset(addr), data, size);
  }

  void reset() {
    for (auto &[name, device] : _devices) {
      device->reset();
    }
  }

  void clock_tick() {
    for (auto &[name, device] : _devices) {
      device->clock_tick();
    }
  }

  std::vector<uint32_t> get_pending_interrupts() const {
    std::vector<uint32_t> interrupts;
    for (const auto &[name, device] : _devices) {
      if (device->has_interrupt()) {
        interrupts.push_back(device->get_interrupt_id());
      }
    }
    return interrupts;
  }

  bool is_mmio_address(addr_t addr) const {
    return find_device(addr) != nullptr;
  }

  std::vector<std::string> list_devices() const {
    std::vector<std::string> names;
    for (const auto &[name, _] : _devices) {
      names.push_back(name);
    }
    return names;
  }

  void dump_device_map() const {
    for (const auto &[name, device] : _devices) {
      printf("%s: 0x%08x - 0x%08x\n", device->name(), device->base_address(),
             device->base_address() + device->address_range() - 1);
    }
  }

private:
  std::map<std::string, std::unique_ptr<IHardware>> _devices;

  IHardware *find_device(addr_t addr) const {
    for (const auto &[name, device] : _devices) {
      if (device->owns_address(addr)) {
        return device.get();
      }
    }
    return nullptr;
  }
};

} // namespace demu::hal
