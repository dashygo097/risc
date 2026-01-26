#pragma once

#include "../isa/isa.hh"

namespace demu::hal {
using namespace isa;

template <typename DUT> class RTLHardware {
public:
  RTLHardware() : _dut(std::make_unique<DUT>()) { _dut->eval(); }
  virtual ~RTLHardware() = default;

  virtual addr_t base_address() const noexcept = 0;
  virtual size_t address_range() const noexcept = 0;

  [[nodiscard]] bool is_valid_offset(addr_t offset) const noexcept {
    return offset < address_range();
  }
  [[nodiscard]] addr_t to_offset(addr_t addr) const noexcept {
    return addr - base_address();
  }
  [[nodiscard]] bool owns_address(addr_t addr) const noexcept {
    addr_t base = base_address();
    return addr >= base && addr < (base + address_range());
  }

  virtual void clock_tick() = 0;
  virtual void reset() = 0;

  virtual const char *name() const noexcept { return "Unknown RTL Hardware"; }

protected:
  std::unique_ptr<DUT> _dut;
};

} // namespace demu::hal
