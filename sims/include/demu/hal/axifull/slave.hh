#pragma once

#include "../../isa/isa.hh"
#include <cstdint>
#include <queue>

namespace demu::hal::axi {
using namespace isa;

class AXIFullSlave {
public:
  virtual ~AXIFullSlave() = default;
  virtual addr_t base_address() const noexcept = 0;
  virtual size_t size() const noexcept = 0;

  [[nodiscard]] bool owns_address(addr_t addr) const noexcept {
    addr_t base = base_address();
    return addr >= base && addr < (base + size());
  }

  virtual void clock_tick() = 0;

  virtual void reset() = 0;

  // AW

  // W

  // B

  // AR

  // R

  virtual const char *name() const noexcept { return "AXIFull Slave"; }
};

} // namespace demu::hal::axi
