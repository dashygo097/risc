#include "demu/hal/allocator.hh"
#include <cstring>
#include <fstream>
#include <iomanip>
#include <sstream>

namespace demu::hal {

MemoryAllocator::MemoryAllocator(addr_t base_addr, size_t size)
    : memory_(size, 0), base_addr_(base_addr) {
  HAL_DEBUG("MemoryAllocator initialized: Size={} bytes, BaseAddr=0x{:08x}",
            size, base_addr);
}

// Helpers
auto MemoryAllocator::load_binary(const std::string &filename, addr_t load_addr)
    -> bool {
  std::ifstream file(filename, std::ios::binary | std::ios::ate);
  if (!file.is_open()) {
    HAL_ERROR("Failed to open binary file: {}", filename);
    return false;
  }

  const auto size = static_cast<size_t>(file.tellg());
  file.seekg(0, std::ios::beg);

  if (!is_valid_addr(load_addr)) {
    HAL_ERROR("Load address 0x{:08x} is not valid for allocator (Base: "
              "0x{:08x}, Size: {})",
              load_addr, base_addr_, memory_.size());
    return false;
  }

  const addr_t offset = to_offset(load_addr);

  if (offset + size > memory_.size()) {
    HAL_ERROR("Binary file ({}) too large for memory (Size: {}, Available: {})",
              filename, size, memory_.size() - offset);
    return false;
  }

  if (!file.read(reinterpret_cast<char *>(memory_.data() + offset), size)) {
    HAL_ERROR("Read error while loading binary: {}", filename);
    return false;
  }

  HAL_INFO("Loaded '{}' ({} bytes) at physical address 0x{:08x} (internal "
           "offset 0x{:0x})",
           filename, size, load_addr, offset);
  return true;
}

void MemoryAllocator::clear() {
  HAL_DEBUG("MemoryAllocator cleared (zeroed)");
  memset(memory_.data(), 0, memory_.size());
}

void MemoryAllocator::dump(addr_t start, addr_t length) const {
  HAL_DEBUG("MemoryAllocator Dump [0x{:08x} - 0x{:08x}]:", start,
            start + length);
  for (addr_t addr = start; addr < start + length; addr += 16) {
    std::stringstream ss;
    ss << std::hex << std::setw(8) << std::setfill('0') << addr << ": ";

    for (size_t i = 0; i < 16 && addr + i < start + length; i++) {
      if (i == 8) {
        ss << " ";
      }
      ss << std::hex << std::setw(2) << std::setfill('0')
         << static_cast<int>(read_byte(addr + i)) << " ";
    }

    ss << " |";
    for (size_t i = 0; i < 16 && addr + i < start + length; i++) {
      byte_t c = read_byte(addr + i);
      ss << (c >= 32 && c < 127 ? static_cast<char>(c) : '.');
    }
    ss << "|";

    HAL_INFO("{}", ss.str());
  }
}

[[nodiscard]] auto MemoryAllocator::is_valid_addr(addr_t addr) const noexcept
    -> bool {
  return addr >= base_addr_ &&
         (addr - base_addr_) < static_cast<addr_t>(memory_.size());
}

[[nodiscard]] auto MemoryAllocator::to_offset(addr_t addr) const noexcept
    -> addr_t {
  return addr - base_addr_;
}

} // namespace demu::hal
