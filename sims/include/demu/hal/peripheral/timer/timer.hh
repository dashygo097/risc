#pragma once

#include "../../allocator.hh"
#include "../../device.hh"

namespace demu::hal::timer {
using namespace isa;

class Timer final : public Device {};

} // namespace demu::hal::timer
