#pragma once

#include "../../allocator.hh"
#include "../../hardware.hh"
#include <cstdint>
#include <functional>
#include <memory>
#include <queue>

namespace demu::hal::gpio {
using namespace isa;

class Gpio final : public Hardware {};

} // namespace demu::hal::gpio
