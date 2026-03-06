#pragma once

#include "../isa/isa.hh"

namespace demu::hal {
using namespace isa;

class Hardware {
public:
  virtual ~Hardware() = default;

  [[nodiscard]] virtual addr_t base_address() const noexcept = 0;
  [[nodiscard]] virtual size_t address_range() const noexcept = 0;

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

  virtual const char *name() const noexcept { return "Unknown Hardware"; }

  virtual void dump(addr_t start, size_t size) const noexcept {}
};

template <typename DUT> class RTLHardware : public Hardware {
public:
  RTLHardware() : dut_(std::make_unique<DUT>()) { dut_->eval(); }
  virtual ~RTLHardware() = default;

protected:
  std::unique_ptr<DUT> dut_;
};

} // namespace demu::hal
