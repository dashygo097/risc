#pragma once

#include "../isa/isa.hh"
#include "./ihardware.hh"

namespace demu::hal {
using namespace isa;

class EmulatedHardware : public IHardware {
public:
  virtual ~EmulatedHardware() = default;

  void clock_tick() override {
    // Default: do nothing
  }

protected:
  EmulatedHardware(addr_t base, size_t range)
      : _base_addr(base), _addr_range(range) {}

  addr_t base_address() const noexcept override { return _base_addr; }
  size_t address_range() const noexcept override { return _addr_range; }

private:
  addr_t _base_addr;
  size_t _addr_range;
};

} // namespace demu::hal
