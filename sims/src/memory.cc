#include "memory.hh"
#include "hex_loader.hh"
#include <cstring>
#include <fstream>
#include <iomanip>
#include <iostream>

Memory::Memory(size_t size, uint32_t base_addr)
    : memory_(size, 0), base_addr_(base_addr) {}

uint32_t Memory::read32(uint32_t addr) const {
  if (!is_valid_addr(addr))
    return 0;
  uint32_t offset = translate_addr(addr);
  return (memory_[offset + 0] << 0) | (memory_[offset + 1] << 8) |
         (memory_[offset + 2] << 16) | (memory_[offset + 3] << 24);
}

uint16_t Memory::read16(uint32_t addr) const {
  if (!is_valid_addr(addr))
    return 0;
  uint32_t offset = translate_addr(addr);
  return (memory_[offset + 0] << 0) | (memory_[offset + 1] << 8);
}

uint8_t Memory::read8(uint32_t addr) const {
  if (!is_valid_addr(addr))
    return 0;
  uint32_t offset = translate_addr(addr);
  return memory_[offset];
}

void Memory::write32(uint32_t addr, uint32_t data) {
  if (!is_valid_addr(addr))
    return;
  uint32_t offset = translate_addr(addr);
  memory_[offset + 0] = (data >> 0) & 0xFF;
  memory_[offset + 1] = (data >> 8) & 0xFF;
  memory_[offset + 2] = (data >> 16) & 0xFF;
  memory_[offset + 3] = (data >> 24) & 0xFF;
}

void Memory::write16(uint32_t addr, uint16_t data) {
  if (!is_valid_addr(addr))
    return;
  uint32_t offset = translate_addr(addr);
  memory_[offset + 0] = (data >> 0) & 0xFF;
  memory_[offset + 1] = (data >> 8) & 0xFF;
}

void Memory::write8(uint32_t addr, uint8_t data) {
  if (!is_valid_addr(addr))
    return;
  uint32_t offset = translate_addr(addr);
  memory_[offset] = data;
}

bool Memory::load_hex(const std::string &filename) {
  std::vector<uint32_t> data;
  if (HexLoader::load_verilog_hex(filename, data)) {
    for (size_t i = 0; i < data.size() && i * 4 < memory_.size(); i++) {
      write32(base_addr_ + i * 4, data[i]);
    }
    return true;
  }

  std::vector<uint8_t> byte_data;
  uint32_t load_addr;
  if (HexLoader::load_intel_hex(filename, byte_data, load_addr)) {
    for (size_t i = 0; i < byte_data.size(); i++) {
      write8(load_addr + i, byte_data[i]);
    }
    return true;
  }

  return false;
}

bool Memory::load_binary(const std::string &filename, uint32_t offset) {
  std::ifstream file(filename, std::ios::binary | std::ios::ate);
  if (!file.is_open()) {
    std::cerr << "Failed to open binary file: " << filename << std::endl;
    return false;
  }

  std::streamsize size = file.tellg();
  file.seekg(0, std::ios::beg);

  if (offset + size > memory_.size()) {
    std::cerr << "Binary file too large for memory" << std::endl;
    return false;
  }

  std::vector<char> buffer(size);
  if (file.read(buffer.data(), size)) {
    for (size_t i = 0; i < buffer.size(); i++) {
      memory_[offset + i] = static_cast<uint8_t>(buffer[i]);
    }
    return true;
  }

  return false;
}

void Memory::clear() { std::fill(memory_.begin(), memory_.end(), 0); }

void Memory::dump(uint32_t start, uint32_t length) const {
  for (uint32_t addr = start; addr < start + length; addr += 16) {
    std::cout << std::hex << std::setw(8) << std::setfill('0') << addr << ": ";

    for (uint32_t i = 0; i < 16 && addr + i < start + length; i++) {
      if (i == 8)
        std::cout << " ";
      std::cout << std::hex << std::setw(2) << std::setfill('0')
                << (int)read8(addr + i) << " ";
    }

    std::cout << " |";
    for (uint32_t i = 0; i < 16 && addr + i < start + length; i++) {
      uint8_t c = read8(addr + i);
      std::cout << (c >= 32 && c < 127 ? (char)c : '.');
    }
    std::cout << "|" << std::endl;
  }
  std::cout << std::dec << std::endl;
}

bool Memory::is_valid_addr(uint32_t addr) const {
  uint32_t offset = addr - base_addr_;
  return offset < memory_.size();
}

uint32_t Memory::translate_addr(uint32_t addr) const {
  return addr - base_addr_;
}
