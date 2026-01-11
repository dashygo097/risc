# Find and configure all external dependencies

# 3rdparty 
include_directories(3rdparty)

find_package(Threads REQUIRED)
find_package(verilator HINTS $ENV{VERILATOR_ROOT})
if(NOT verilator_FOUND)
  message(FATAL_ERROR "Verilator not found. Please install Verilator or set VERILATOR_ROOT")
else()
  if(ENABLE_TRACE)
    list(APPEND VERILATOR_ARGS --trace)
    add_definitions(-DENABLE_TRACE)
  endif()

  if(ENABLE_COVERAGE)
    list(APPEND VERILATOR_ARGS --coverage)
  endif()

  if(ENABLE_SYSTEM)
    add_compile_definitions(ENABLE_SYSTEM)
  endif()

  if(${ISA} STREQUAL "rv32i")
    set(__ISA_RV32I__ TRUE CACHE INTERNAL "rv32i is available")
    add_compile_definitions(__ISA_RV32I__) 
  else()
    message(FATAL_ERROR "Unsupported ISA: ${ISA}. Supported ISAs: rv32i")
  endif()

endif()


