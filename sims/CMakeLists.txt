cmake_minimum_required(VERSION 3.21)
project(rvsim LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

set(CMAKE_CXX_FLAGS_DEBUG "-g -O0 -Wall -Wextra")
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -Wall")

find_program(CCACHE_PROGRAM ccache)
if(CCACHE_PROGRAM)
    set(CMAKE_C_COMPILER_LAUNCHER ${CCACHE_PROGRAM})
    set(CMAKE_CXX_COMPILER_LAUNCHER ${CCACHE_PROGRAM})
    message(STATUS "Using ccache: ${CCACHE_PROGRAM}")
endif()

if(APPLE)
    set(IS_APPLE TRUE)
    message(STATUS "Building on macOS")
elseif(WIN32)
    set(IS_WINDOWS TRUE)
    message(STATUS "Building on Windows")
else()
    set(IS_UNIX TRUE)
    message(STATUS "Building on Unix-like system")
endif()

# 3rdparty 
include_directories(3rdparty)

find_package(verilator HINTS $ENV{VERILATOR_ROOT})
if(NOT verilator_FOUND)
    message(FATAL_ERROR "Verilator not found. Please install Verilator or set VERILATOR_ROOT")
endif()

option(ENABLE_TRACE "Enable VCD tracing" ON)
option(ENABLE_COVERAGE "Enable coverage collection" OFF)
option(BUILD_TESTS "Build test programs" OFF)

# rvsim 
include_directories(${CMAKE_SOURCE_DIR}/include)
include_directories(${CMAKE_BINARY_DIR})

set(RTL_SOURCES ${CMAKE_SOURCE_DIR}/../build/rv32_cpu.sv)
if(NOT EXISTS ${RTL_SOURCES})
  message(WARNING "RTL file not found: ${RTL_SOURCES}")
  message(WARNING "Please make sure your Chisel design has been generated")
endif()

set(VERILATOR_ARGS
  -Wall
  -Wno-WIDTH
  -Wno-UNUSED
  -Wno-UNOPTFLAT
  -Wno-DECLFILENAME
)

if(ENABLE_TRACE)
  list(APPEND VERILATOR_ARGS --trace)
  add_definitions(-DENABLE_TRACE)
endif()

if(ENABLE_COVERAGE)
  list(APPEND VERILATOR_ARGS --coverage)
endif()

file(MAKE_DIRECTORY ${CMAKE_BINARY_DIR}/bin)
add_subdirectory(src)

# Colored printing
function(print_info MSG COLOR)
  if(CMAKE_COLOR_MAKEFILE)
    execute_process(COMMAND printf "\\033[1;${COLOR}m${MSG}\\033[0m\n")
  else()
    message(STATUS "${MSG}")
  endif()
endfunction()

function(make_paths_relative out_var)
    set(result "")
    foreach(path IN LISTS ${ARGV1})
        file(RELATIVE_PATH rel "${CMAKE_SOURCE_DIR}" "${path}")
        list(APPEND result "${rel}")
    endforeach()
    set(${out_var} "${result}" PARENT_SCOPE)
endfunction()

function(make_preview_string LIST_VAR N)
  set(preview "")

  list(LENGTH ${LIST_VAR} list_length)

  foreach(item IN LISTS ${LIST_VAR})
    list(APPEND preview "${item}")
    if("${preview}" MATCHES "^.*;.*;.*$")
      break()
    endif()
  endforeach()

  if(list_length GREATER N)
    math(EXPR remaining_items "${list_length} - ${N}")
    list(APPEND preview "; ...(+${remaining_items} more)")
  endif()

  set(PREVIOUS_SCOPE_VAR "${preview}" PARENT_SCOPE)
endfunction()

# Stastics
print_info("――――――――――――――――――APP SCOPE――――――――――――――――――" "0")
make_paths_relative(REL_HEADERS VHN_HEADERS)

make_preview_string(REL_HEADERS 3)
print_info("[TRACE] Header files: ${PREVIOUS_SCOPE_VAR}" "94")

if(BUILD_TESTS)
  enable_testing()
  add_subdirectory(tests)
endif()

# App
install(TARGETS rv32_simulator DESTINATION bin)
print_info("Configuration: " "92")
print_info("  Build Type: ${CMAKE_BUILD_TYPE}" "92")
print_info("  C++ Compiler: ${CMAKE_CXX_COMPILER}" "92")
print_info("  Verilator: ${VERILATOR_ROOT}" "92")
print_info("  RTL Source: ${RTL_SOURCES}" "92")
print_info("  Enable Trace: ${ENABLE_TRACE}" "92")
print_info("  Enable Coverage: ${ENABLE_COVERAGE}" "92")
print_info("  Enable Tests: ${BUILD_TESTS}" "92")
