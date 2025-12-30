#pragma once

#include <cstdint>
#include <string>
#include <vector>

class Memory {
public:
  Memory(size_t size, uint32_t base_addr = 0x0);
  ~Memory() = default;

  [[nodiscard]] uint32_t read32(uint32_t addr) const noexcept;
  [[nodiscard]] uint16_t read16(uint32_t addr) const noexcept;
  [[nodiscard]] uint8_t read8(uint32_t addr) const noexcept;

  void write32(uint32_t addr, uint32_t data);
  void write16(uint32_t addr, uint16_t data);
  void write8(uint32_t addr, uint8_t data);

  bool load_binary(const std::string &filename, uint32_t offset = 0);
  void clear();

  [[nodiscard]] size_t size() const noexcept { return _memory.size(); }
  [[nodiscard]] uint32_t base_addr() const noexcept { return _base_addr; }

  void dump(uint32_t start, uint32_t length) const;

private:
  std::vector<uint8_t> _memory;
  uint32_t _base_addr;

  [[nodiscard]] bool is_valid_addr(uint32_t addr) const noexcept;
  [[nodiscard]] uint32_t translate_addr(uint32_t addr) const noexcept;
};
