#pragma once

#include "../../isa/isa.hh"
#include <cstdint>
#include <queue>

namespace demu::hal {
using namespace isa;

struct AXIReadTransaction {
  addr_t addr;
  bool active;
  word_t data;
  bool valid;
};

struct AXIWriteTransaction {
  addr_t addr;
  word_t data;
  byte_t strb;
  bool addr_valid;
  bool data_valid;
  bool resp_ready;
};

class AXISlave {
public:
  virtual ~AXISlave() = default;

  virtual addr_t base_address() const noexcept = 0;
  virtual size_t address_range() const noexcept = 0;

  [[nodiscard]] bool owns_address(addr_t addr) const noexcept {
    addr_t base = base_address();
    return addr >= base && addr < (base + address_range());
  }

  virtual void clock_tick() = 0;

  virtual void reset() = 0;

  virtual void aw_valid(addr_t addr) = 0;
  virtual bool aw_ready() const noexcept = 0;

  virtual void w_valid(word_t data, byte_t strb) = 0;
  virtual bool w_ready() const noexcept = 0;

  virtual bool b_valid() const noexcept = 0;
  virtual void b_ready(bool ready) = 0;
  virtual uint8_t b_resp() const noexcept = 0;

  virtual void ar_valid(addr_t addr) = 0;
  virtual bool ar_ready() const noexcept = 0;

  virtual bool r_valid() const noexcept = 0;
  virtual void r_ready(bool ready) = 0;
  virtual word_t r_data() const noexcept = 0;
  virtual uint8_t r_resp() const noexcept = 0;

  virtual const char *name() const noexcept { return "AXI Slave"; }

protected:
  [[nodiscard]] addr_t to_offset(addr_t addr) const noexcept {
    return addr - base_address();
  }
};

} // namespace demu::hal
