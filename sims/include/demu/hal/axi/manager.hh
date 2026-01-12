#pragma once

#include "./slave.hh"
#include <memory>
#include <vector>

namespace demu::hal {

class AXIBusManager {
public:
  AXIBusManager() = default;
  ~AXIBusManager() = default;

  template <typename T, typename... Args>
  T *register_slave(uint8_t port, const std::string &name, Args &&...args) {
    if (port >= _slaves.size()) {
      _slaves.resize(port + 1);
      _slave_names.resize(port + 1);
    }

    std::unique_ptr<T> slave;
    try {
      slave = std::make_unique<T>(std::forward<Args>(args)...);
    } catch (const std::exception &e) {
      std::cerr << "Failed to create slave '" << name << "': " << e.what()
                << std::endl;
      return nullptr;
    }

    T *ptr = slave.get();
    _slaves[port] = std::move(slave);
    _slave_names[port] = name;

    return ptr;
  }

  AXISlave *get_slave(uint8_t port) {
    if (port < _slaves.size()) {
      return _slaves[port].get();
    }
    return nullptr;
  }

  template <typename T> T *get_slave(uint8_t port) {
    return dynamic_cast<T *>(get_slave(port));
  }

  AXISlave *find_slave_for_address(addr_t addr) {
    for (auto &slave : _slaves) {
      if (slave && slave->owns_address(addr)) {
        return slave.get();
      }
    }
    return nullptr;
  }

  const AXISlave *find_slave_for_address(addr_t addr) const {
    for (const auto &slave : _slaves) {
      if (slave && slave->owns_address(addr)) {
        return slave.get();
      }
    }
    return nullptr;
  }

  void reset() {
    for (auto &slave : _slaves) {
      if (slave) {
        slave->reset();
      }
    }
  }

  void clock_tick() {
    for (auto &slave : _slaves) {
      if (slave) {
        slave->clock_tick();
      }
    }
  }

  void dump_device_map() const {
    for (size_t i = 0; i < _slaves.size(); i++) {
      if (_slaves[i]) {
        printf("M_AXI_%zu: %s [0x%08x - 0x%08zx]\n", i, _slave_names[i].c_str(),
               _slaves[i]->base_address(),
               _slaves[i]->base_address() + _slaves[i]->address_range() - 1);
      }
    }
  }

private:
  std::vector<std::unique_ptr<AXISlave>> _slaves;
  std::vector<std::string> _slave_names;
};

} // namespace demu::hal
