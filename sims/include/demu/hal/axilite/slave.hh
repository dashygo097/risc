#pragma once

#include "../emu.hh"
#include <cstdint>

namespace demu::hal::axi {
using namespace isa;

class AXILiteSlave : public EmulatedHardware {
public:
  virtual ~AXILiteSlave() override = default;

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
};

} // namespace demu::hal::axi
