cmake_minimum_required(VERSION 3.21)
project(demu LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

set(CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")
message(STATUS "CMake module path: ${CMAKE_MODULE_PATH}")
message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")

if(EXISTS "${CMAKE_CURRENT_BINARY_DIR}/config.cmake")
  include("${CMAKE_CURRENT_BINARY_DIR}/config.cmake")
endif()

include(Helpers)
include(CompilerSetup)
include(Dependencies)
include(SourceCollections)
include(LibraryTargets)

add_subdirectory(tools)
if (ENABLE_TESTING)
  add_subdirectory(tests)
endif()
