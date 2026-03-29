#pragma once

#include "Vrv32im_system.h"
#include <cstdint>

#define ISA_NAME "rv32im"
enum { NUM_GPRS = 32, INSTR_ALIGNMENT = 4 };

namespace demu::isa {
using system_t = Vrv32im_system;

using instr_t = uint32_t;
using addr_t = uint32_t;
using int_t = int32_t;
using word_t = uint32_t;
using half_t = uint16_t;
using byte_t = uint8_t;

} // namespace demu::isa
