#include "demu/hal/peripheral/uart/uart.hh"

namespace demu::hal::uart {
using namespace isa;

void Uart::clock_tick() {}

void Uart::reset() {}

void Uart::dump(addr_t start, size_t size) const noexcept {};

} // namespace demu::hal::uart
