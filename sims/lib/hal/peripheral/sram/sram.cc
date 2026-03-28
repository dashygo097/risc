#include "demu/hal/peripheral/sram/sram.hh"

namespace demu::hal::sram {

void SRAM::clock_tick() {}

void SRAM::reset() { memory_->clear(); }

void SRAM::dump(addr_t start, size_t size) const noexcept {
  if (!owns_address(start)) {
    HAL_WARN("SRAM dump: 0x{:08X} not owned", static_cast<uint64_t>(start));
    return;
  }

  const size_t clamped = std::min(
      size, static_cast<size_t>(base_address() + address_range() - start));

  HAL_INFO("SRAM dump [0x{:08X} - 0x{:08X}]:", static_cast<uint64_t>(start),
           static_cast<uint64_t>(start + clamped));

  const byte_t *ptr = memory_->get_ptr(start);
  if (!ptr) {
    return;
}

  for (size_t i = 0; i < clamped; i += 16) {
    std::stringstream ss;
    ss << std::hex << std::setw(8) << std::setfill('0')
       << static_cast<uint64_t>(start + i) << ": ";

    for (size_t j = 0; j < 16; ++j) {
      if (i + j < clamped) {
        ss << std::hex << std::setw(2) << std::setfill('0')
           << static_cast<int>(ptr[i + j]) << ' ';
      } else {
        ss << "   ";
}
      if (j == 7) {
        ss << ' ';
}
    }

    ss << " |";
    for (size_t j = 0; j < 16 && (i + j) < clamped; ++j) {
      const byte_t c = ptr[i + j];
      ss << static_cast<char>((c >= 32 && c < 127) ? c : '.');
    }
    ss << '|';
    HAL_INFO("{}", ss.str());
  }
}

} // namespace demu::hal::sram
