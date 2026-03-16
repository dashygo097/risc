#pragma once

#include "../../allocator.hh"
#include "../../hardware.hh"

namespace demu::hal::gpio {
using namespace isa;

class Gpio final : public Hardware {};

} // namespace demu::hal::gpio
