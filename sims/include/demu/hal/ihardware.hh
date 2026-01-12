#pragma once

#include "../isa/isa.hh"

namespace demu::hal {
using namespace isa;

struct MMIOResult {
  bool success;
  word_t data;
  std::string error_msg;

  static MMIOResult ok(word_t data = 0) { return {true, data, ""}; }

  static MMIOResult err(const std::string &msg) { return {false, 0, msg}; }
};

class IHardware {
public:
  virtual ~IHardware() = default;

  virtual MMIOResult read(addr_t offset, size_t size) = 0;
  virtual MMIOResult write(addr_t offset, word_t data, size_t size) = 0;

  virtual addr_t base_address() const noexcept = 0;
  virtual size_t address_range() const noexcept = 0;

  [[nodiscard]] bool owns_address(addr_t addr) const noexcept {
    addr_t base = base_address();
    return addr >= base && addr < (base + address_range());
  }

  virtual void reset() = 0;
  virtual void clock_tick() = 0;

  virtual const char *name() const noexcept { return "Unknown Hardware"; }

  virtual bool has_interrupt() const noexcept { return false; }
  virtual uint32_t get_interrupt_id() const noexcept { return 0; }
  virtual void clear_interrupt() {}

  [[nodiscard]] bool is_valid_offset(addr_t offset) const noexcept {
    return offset < address_range();
  }

  [[nodiscard]] addr_t to_offset(addr_t addr) const noexcept {
    return addr - base_address();
  }
};

} // namespace demu::hal
