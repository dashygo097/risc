#ifndef MEMORY_H
#define MEMORY_H

#include <cstdint>
#include <string>
#include <vector>

class Memory {
public:
  Memory(size_t size, uint32_t base_addr = 0);
  ~Memory() = default;

  // Read/Write operations
  uint32_t read32(uint32_t addr) const;
  uint16_t read16(uint32_t addr) const;
  uint8_t read8(uint32_t addr) const;

  void write32(uint32_t addr, uint32_t data);
  void write16(uint32_t addr, uint16_t data);
  void write8(uint32_t addr, uint8_t data);

  // Bulk operations
  bool load_hex(const std::string &filename);
  bool load_binary(const std::string &filename, uint32_t offset = 0);
  void clear();

  // Info
  size_t size() const { return memory_.size(); }
  uint32_t base_addr() const { return base_addr_; }

  // Dump
  void dump(uint32_t start, uint32_t length) const;

private:
  std::vector<uint8_t> memory_;
  uint32_t base_addr_;

  bool is_valid_addr(uint32_t addr) const;
  uint32_t translate_addr(uint32_t addr) const;
};

#endif // MEMORY_H
