#pragma once

#include "./allocator.hh"
#include "./device_manager.hh"
#include "./hardware.hh"
#include "./port_handler.hh"

// Peripherals
// GPIO
#include "./peripheral/gpio/gpio.hh"
#include "./peripheral/gpio/port_handler.hh"

// Bus
// AXI4-Lite
#include "./bus/axil/memory.hh"
#include "./bus/axil/port_handler.hh"
#include "./bus/axil/signals.hh"
#include "./bus/axil/slave.hh"
