#pragma once

#include "./isa/isa.hh"
#include <cstdint>
#include <string>
#include <vector>

namespace demu {
using namespace isa;

class Memory {
public:
  Memory(size_t size, addr_t base_addr = 0x0);
  ~Memory() = default;

  [[nodiscard]] word_t read_word(addr_t addr) const noexcept;
  [[nodiscard]] half_t read_half(addr_t addr) const noexcept;
  [[nodiscard]] byte_t read_byte(addr_t addr) const noexcept;

  void write_word(addr_t addr, word_t data);
  void write_half(addr_t addr, half_t data);
  void write_byte(addr_t addr, byte_t data);

  bool load_binary(const std::string &filename, addr_t offset = 0);
  void dump(addr_t start, addr_t length) const;
  void clear();

  [[nodiscard]] size_t size() const noexcept { return _memory.size(); }
  [[nodiscard]] addr_t base_addr() const noexcept { return _base_addr; }

private:
  std::vector<byte_t> _memory;
  addr_t _base_addr;

  [[nodiscard]] bool is_valid_addr(addr_t addr) const noexcept;
  [[nodiscard]] addr_t translate_addr(addr_t addr) const noexcept;
};

} // namespace demu
