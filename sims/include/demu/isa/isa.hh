#pragma once

#ifdef __ISA_RV32I__
#include "./rv32i/instruction.hh"
#include "./rv32i/typedefs.hh"
#include "Vrv32i_system.h"
#endif

#ifdef __ISA_RV32IM__
#include "./rv32im/instruction.hh"
#include "./rv32im/typedefs.hh"
#include "Vrv32im_system.h"
#endif
