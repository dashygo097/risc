add_executable(${ISA}_cpu_verilated)

verilate(${ISA}_cpu_verilated
  SOURCES ${CPU_RTL_SOURCE}
  VERILATOR_ARGS 
    -Wall
    -Wno-WIDTH
    -Wno-UNUSED
    -Wno-UNOPTFLAT
    -Wno-DECLFILENAME
    --top-module ${ISA}_cpu
  PREFIX V${ISA}_cpu
  TRACE_THREADS 2
)

# Simulator sources
set(SIMULATOR_SOURCES
  ${ISA}/main.cc
  ${ISA}/cpu_sim.cc
)

target_sources(${ISA}_cpu_verilated PRIVATE ${SIMULATOR_SOURCES})

target_include_directories(${ISA}_cpu_verilated PRIVATE
  ${ISA}/cpu_sim.hh
)

find_package(Threads REQUIRED)
target_link_libraries(${ISA}_cpu_verilated PRIVATE rvsim Threads::Threads)

set_target_properties(${ISA}_cpu_verilated PROPERTIES
  OUTPUT_NAME ${ISA}_simulator
  RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin
)

add_executable(${ISA}_simulator ALIAS ${ISA}_cpu_verilated)

# Stastics
print_info("――――――――――――――――――APP SCOPE――――――――――――――――――\n" "0" "1")

# App
install(TARGETS ${ISA}_cpu_verilated DESTINATION bin)
print_info("Configuration: \n" "92" "0")
print_info("  ISA: ${ISA}\n" "92" "1")
print_info("  Build Type: ${CMAKE_BUILD_TYPE}\n" "92" "1")
print_info("  C++ Compiler: ${CMAKE_CXX_COMPILER}\n" "92" "1")
print_info("  Verilator: ${VERILATOR_ROOT}\n" "92" "1")
print_info("  RTL Source: ${RTL_SOURCES}\n" "92" "1")
print_info("  Enable Trace: ${ENABLE_TRACE}\n" "94" "2")
print_info("  Enable Coverage: ${ENABLE_COVERAGE}\n" "94" "2")
