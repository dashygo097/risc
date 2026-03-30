#pragma once

#include "./allocator.hh"
#include "./device.hh"
#include "./device_manager.hh"
#include "./hardware.hh"
#include "./interrupt.hh"
#include "./port_handler.hh"

// Peripherals
// SRAM
#include "./peripheral/sram/port_handler.hh"
#include "./peripheral/sram/signals.hh"
#include "./peripheral/sram/sram.hh"

// Bus
// AXI4-Lite
#include "./bus/axil/port_handler.hh"
#include "./bus/axil/signals.hh"
#include "./bus/axil/slave.hh"
#include "./bus/axil/sram.hh"

// AXI4-Full
#include "./bus/axif/port_handler.hh"
#include "./bus/axif/signals.hh"
#include "./bus/axif/slave.hh"
#include "./bus/axif/sram.hh"
