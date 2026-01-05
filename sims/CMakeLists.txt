cmake_minimum_required(VERSION 3.21)
project(demu LANGUAGES C CXX)

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
include(LibraryTargets)

add_subdirectory(tools)
