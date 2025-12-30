cmake_minimum_required(VERSION 3.21)
project(rvsim LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Add cmake module path
set(CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")
message(STATUS "CMake module path: ${CMAKE_MODULE_PATH}")

# Generate and include build configuration
if(EXISTS "${CMAKE_CURRENT_BINARY_DIR}/config.cmake")
  include("${CMAKE_CURRENT_BINARY_DIR}/config.cmake")
endif()

include(Helpers)
include(CompilerSetup)
include(Dependencies)
include(SourceCollections)

# rvsim 
include_directories(${CMAKE_SOURCE_DIR}/include)
include_directories(${CMAKE_BINARY_DIR})

set(RTL_SOURCES ${CMAKE_SOURCE_DIR}/../build/rv32i_cpu.sv)
if(NOT EXISTS ${RTL_SOURCES})
  message(WARNING "RTL file not found: ${RTL_SOURCES}")
  message(WARNING "Please make sure your Chisel design has been generated")
endif()

file(MAKE_DIRECTORY ${CMAKE_BINARY_DIR}/bin)
add_subdirectory(src)

# Stastics
print_info("――――――――――――――――――APP SCOPE――――――――――――――――――\n" "0" "1")

# App
install(TARGETS rv32i_cpu_verilated DESTINATION bin)
print_info("Configuration: \n" "92" "0")
print_info("  Build Type: ${CMAKE_BUILD_TYPE}\n" "92" "1")
print_info("  C++ Compiler: ${CMAKE_CXX_COMPILER}\n" "92" "1")
print_info("  Verilator: ${VERILATOR_ROOT}\n" "92" "1")
print_info("  RTL Source: ${RTL_SOURCES}\n" "92" "1")
print_info("  Enable Trace: ${ENABLE_TRACE}\n" "94" "2")
print_info("  Enable Coverage: ${ENABLE_COVERAGE}\n" "94" "2")
