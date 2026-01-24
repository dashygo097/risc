#pragma once

#include "../../isa/isa.hh"
#include <cstdint>

namespace demu::hal::axi {
using namespace isa;

class AXILiteSlave {
public:
  virtual ~AXILiteSlave() = default;

  virtual addr_t base_address() const noexcept = 0;
  virtual size_t size() const noexcept = 0;

  [[nodiscard]] addr_t to_offset(addr_t addr) const noexcept {
    return addr - base_address();
  }
  [[nodiscard]] bool owns_address(addr_t addr) const noexcept {
    addr_t base = base_address();
    return addr >= base && addr < (base + size());
  }

  virtual void clock_tick() = 0;
  virtual void reset() = 0;

  // AW
  virtual void aw_valid(addr_t addr) = 0;
  virtual bool aw_ready() const noexcept = 0;

  // W
  virtual void w_valid(word_t data, byte_t strb) = 0;
  virtual bool w_ready() const noexcept = 0;

  // B
  virtual bool b_valid() const noexcept = 0;
  virtual void b_ready(bool ready) = 0;
  virtual uint8_t b_resp() const noexcept = 0;

  // AR
  virtual void ar_valid(addr_t addr) = 0;
  virtual bool ar_ready() const noexcept = 0;

  // R
  virtual bool r_valid() const noexcept = 0;
  virtual void r_ready(bool ready) = 0;
  virtual word_t r_data() const noexcept = 0;
  virtual uint8_t r_resp() const noexcept = 0;

  virtual const char *name() const noexcept { return "AXILite Slave"; }
};

} // namespace demu::hal::axi
