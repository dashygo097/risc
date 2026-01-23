#pragma once

#include "../../isa/isa.hh"
#include <cstdint>

namespace demu::hal::axi {
using namespace isa;

class AXIFullSlave {
public:
  virtual ~AXIFullSlave() = default;

  [[nodiscard]] virtual addr_t base_address() const noexcept = 0;
  [[nodiscard]] virtual size_t size() const noexcept = 0;

  [[nodiscard]] bool owns_address(addr_t addr) const noexcept {
    addr_t base = base_address();
    return addr >= base && addr < (base + size());
  }

  virtual void clock_tick() = 0;

  virtual void reset() = 0;

  // AW channel
  virtual void aw_valid(addr_t addr, uint16_t id, uint8_t len, uint8_t size,
                        uint8_t burst, uint8_t lock, uint8_t cache,
                        uint8_t prot, uint8_t qos, uint8_t region) = 0;
  virtual bool aw_ready() const noexcept = 0;

  // W channel
  virtual void w_valid(word_t data, byte_t strb, bool last, uint16_t id) = 0;
  virtual bool w_ready() const noexcept = 0;

  // B channel
  virtual bool b_valid() const noexcept = 0;
  virtual void b_ready(bool ready) = 0;
  virtual uint8_t b_resp() const noexcept = 0;
  virtual uint16_t b_id() const noexcept = 0;

  // AR channel
  virtual void ar_valid(addr_t addr, uint16_t id, uint8_t len, uint8_t size,
                        uint8_t burst, uint8_t lock, uint8_t cache,
                        uint8_t prot, uint8_t qos, uint8_t region) = 0;
  virtual bool ar_ready() const noexcept = 0;

  // R channel
  virtual bool r_valid() const noexcept = 0;
  virtual void r_ready(bool ready) = 0;
  virtual word_t r_data() const noexcept = 0;
  virtual uint8_t r_resp() const noexcept = 0;
  virtual bool r_last() const noexcept = 0;
  virtual uint16_t r_id() const noexcept = 0;

  virtual const char *name() const noexcept { return "AXIFull Slave"; }

protected:
  [[nodiscard]] addr_t to_offset(addr_t addr) const noexcept {
    return addr - base_address();
  }
};

} // namespace demu::hal::axi
