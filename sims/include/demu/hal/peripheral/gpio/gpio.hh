#pragma once

#include "../../allocator.hh"
#include "../../device.hh"

namespace demu::hal::gpio {
using namespace isa;

class Gpio final : public Device {};

} // namespace demu::hal::gpio
