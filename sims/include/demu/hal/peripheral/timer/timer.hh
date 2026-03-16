#pragma once

#include "../../allocator.hh"
#include "../../hardware.hh"

namespace demu::hal::timer {
using namespace isa;

class Timer final : public Hardware {};

} // namespace demu::hal::timer
