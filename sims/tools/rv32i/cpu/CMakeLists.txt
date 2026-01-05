add_executable(${ISA}_cpu_verilated)

# Simulator sources
target_sources(${ISA}_cpu_verilated PRIVATE cpu_sim.cpp cpu_main.cpp)
target_include_directories(${ISA}_cpu_verilated PRIVATE
  cpu_sim.hpp
)

target_link_libraries(${ISA}_cpu_verilated PRIVATE demu Threads::Threads)

set_target_properties(${ISA}_cpu_verilated PROPERTIES
  OUTPUT_NAME demu_${ISA}_cpu
  RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin
)

add_executable(demu_${ISA}_cpu ALIAS ${ISA}_cpu_verilated)

# Configuration 
install(TARGETS ${ISA}_cpu_verilated DESTINATION bin)
print_info("Configuration: \n" "92" "0")
print_info("  ISA: ${ISA}\n" "92" "1")
print_info("  Build Type: ${CMAKE_BUILD_TYPE}\n" "92" "1")
print_info("  C++ Compiler: ${CMAKE_CXX_COMPILER}\n" "92" "1")
print_info("  Verilator: ${VERILATOR_ROOT}\n" "92" "1")
print_info("  RTL Source: ${RTL_SOURCES}\n" "92" "1")
print_info("  Enable Trace: ${ENABLE_TRACE}\n" "94" "2")
print_info("  Enable Coverage: ${ENABLE_COVERAGE}\n" "94" "2")
