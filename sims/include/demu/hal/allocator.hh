#pragma once

#include "../isa/isa.hh"
#include "../logger.hh"
#include <string>
#include <vector>

namespace demu::hal {
using namespace isa;

class MemoryAllocator final {
public:
  MemoryAllocator(size_t size, addr_t base_addr = 0x0);
  ~MemoryAllocator() = default;

  template <typename T> [[nodiscard]] T read(addr_t addr) const noexcept {
    if (!is_valid_addr(addr)) {
      HAL_WARN("Invalid Read at 0x{:08x}", addr);
      return 0;
    }
    T val;
    std::memcpy(&val, memory_.data() + to_offset(addr), sizeof(T));
    return val;
  }
  [[nodiscard]] inline word_t read_word(addr_t addr) const noexcept {
    return read<word_t>(addr);
  }
  [[nodiscard]] inline half_t read_half(addr_t addr) const noexcept {
    return read<half_t>(addr);
  }
  [[nodiscard]] inline byte_t read_byte(addr_t addr) const noexcept {
    return read<byte_t>(addr);
  }

  template <typename T> void write(addr_t addr, T data) {
    if (!is_valid_addr(addr)) {
      HAL_WARN("Invalid Write at 0x{:08x}", addr);
      return;
    }
    std::memcpy(memory_.data() + to_offset(addr), &data, sizeof(T));
  }
  inline void write_word(addr_t addr, word_t data) {
    write<word_t>(addr, data);
  }
  inline void write_half(addr_t addr, half_t data) {
    write<half_t>(addr, data);
  }
  inline void write_byte(addr_t addr, byte_t data) {
    write<byte_t>(addr, data);
  }

  // Helpers
  [[nodiscard]] bool is_valid_addr(addr_t addr) const noexcept;
  [[nodiscard]] addr_t to_offset(addr_t addr) const noexcept;
  bool load_binary(const std::string &filename, addr_t offset = 0);
  void dump(addr_t start, addr_t length) const;
  void clear();

  // Direct access
  [[nodiscard]] byte_t *data() noexcept { return memory_.data(); }
  [[nodiscard]] size_t size() const noexcept { return memory_.size(); }
  [[nodiscard]] addr_t base_address() const noexcept { return base_addr_; }
  [[nodiscard]] byte_t *get_ptr(addr_t addr) {
    if (!is_valid_addr(addr)) {
      HAL_WARN("Invalid memory access at address 0x{:08x}", addr);
      return nullptr;
    }
    return &memory_[to_offset(addr)];
  }

private:
  // components
  std::vector<byte_t> memory_;
  addr_t base_addr_;
};

} // namespace demu::hal
