cmake_minimum_required(VERSION 3.21)
project(demu LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Apps
add_subdirectory(cpu)
if(ENABLE_SYSTEM)
  add_subdirectory(system)
endif()

# Configuration 
print_info("Configuration: \n" "92" "0")
print_info("  ISA: ${ISA}\n" "92" "1")
print_info("  Build Type: ${CMAKE_BUILD_TYPE}\n" "92" "1")
print_info("  C++ Compiler: ${CMAKE_CXX_COMPILER}\n" "92" "1")
print_info("  Verilator: ${VERILATOR_ROOT}\n" "92" "1")
print_info("  RTL Source: ${RTL_SOURCES}\n" "92" "1")
print_info("  Enable Trace: ${ENABLE_TRACE}\n" "94" "2")
print_info("  Enable Coverage: ${ENABLE_COVERAGE}\n" "94" "2")
