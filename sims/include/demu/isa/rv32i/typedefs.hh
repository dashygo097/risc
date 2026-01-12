#pragma once

#include "Vrv32i_cpu.h"
#include <cstdint>

#ifdef ENABLE_SYSTEM
#include "Vrv32i_system.h"
#endif

#define ISA_NAME "rv32i"
#define NUM_GPRS 32

namespace demu::isa {

using cpu_t = Vrv32i_cpu;
#ifdef ENABLE_SYSTEM
using system_t = Vrv32i_system;
#endif

using instr_t = uint32_t;
using addr_t = uint32_t;
using int_t = int32_t;
using word_t = uint32_t;
using half_t = uint16_t;
using byte_t = uint8_t;

} // namespace demu::isa
