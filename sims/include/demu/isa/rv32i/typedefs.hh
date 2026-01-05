#pragma once

#include <cstdint>

#define NUM_GPRS 32
#define INCLUDE_VERILATOR #include "Vrv32i_cpu.h"

namespace demu::isa {
using instr_t = uint32_t;
using addr_t = uint32_t;
using int_t = int32_t;
using word_t = uint32_t;
using half_t = uint16_t;
using byte_t = uint8_t;
} // namespace demu::isa
