#include "demu/hal/allocator.hh"
#include <cstring>
#include <fstream>
#include <iomanip>
#include <sstream>

namespace demu::hal {

MemoryAllocator::MemoryAllocator(size_t size, addr_t base_addr)
    : memory_(size, 0), base_addr_(base_addr) {
  HAL_INFO("MemoryAllocator initialized: Size={} bytes, BaseAddr=0x{:08x}",
           size, base_addr);
}

// Helpers
bool MemoryAllocator::load_binary(const std::string &filename,
                                  addr_t load_offset) {
  std::ifstream file(filename, std::ios::binary | std::ios::ate);
  if (!file.is_open()) {
    HAL_ERROR("Failed to open binary file: {}", filename);
    return false;
  }

  const auto size = static_cast<size_t>(file.tellg());
  file.seekg(0, std::ios::beg);

  if (load_offset + size > memory_.size()) {
    HAL_ERROR("Binary file ({}) too large for memory (Size: {}, Available: {})",
              filename, size, memory_.size() - load_offset);
    return false;
  }

  if (!file.read(reinterpret_cast<char *>(memory_.data() + load_offset),
                 size)) {
    HAL_ERROR("Read error while loading binary: {}", filename);
    return false;
  }
  HAL_INFO("Loaded '{}' ({} bytes) at offset 0x{:08x}", filename, size,
           load_offset);
  return true;
}

void MemoryAllocator::clear() {
  HAL_DEBUG("MemoryAllocator cleared (zeroed)");
  memset(memory_.data(), 0, memory_.size());
}

void MemoryAllocator::dump(addr_t start, addr_t length) const {
  HAL_INFO("MemoryAllocator Dump [0x{:08x} - 0x{:08x}]:", start,
           start + length);
  for (addr_t addr = start; addr < start + length; addr += 16) {
    std::stringstream ss;
    ss << std::hex << std::setw(8) << std::setfill('0') << addr << ": ";

    for (size_t i = 0; i < 16 && addr + i < start + length; i++) {
      if (i == 8)
        ss << " ";
      ss << std::hex << std::setw(2) << std::setfill('0')
         << (int)read_byte(addr + i) << " ";
    }

    ss << " |";
    for (size_t i = 0; i < 16 && addr + i < start + length; i++) {
      byte_t c = read_byte(addr + i);
      ss << (c >= 32 && c < 127 ? (char)c : '.');
    }
    ss << "|";

    HAL_INFO("{}", ss.str());
  }
}

[[nodiscard]] bool MemoryAllocator::is_valid_addr(addr_t addr) const noexcept {
  return addr >= base_addr_ &&
         (addr - base_addr_) < static_cast<addr_t>(memory_.size());
}

[[nodiscard]] addr_t MemoryAllocator::to_offset(addr_t addr) const noexcept {
  return addr - base_addr_;
}

} // namespace demu::hal
