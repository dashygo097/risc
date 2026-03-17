#include "demu/hal/peripheral/sram/sram.hh"

namespace demu::hal::sram {

void SRAM::clock_tick() {}

void SRAM::reset() {}

void SRAM::dump(addr_t start, size_t size) const noexcept {}

} // namespace demu::hal::sram
