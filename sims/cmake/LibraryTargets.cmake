# demu

add_library(demu
  ${DEMU_SOURCES}
  ${DEMU_HEADERS}
  ${PROTO_GENERATED_SOURCES} 
)

add_dependencies(demu gen_proto)

verilate(demu
  SOURCES ${RTL_SOURCE}
  VERILATOR_ARGS
    -Wall
    -Wno-WIDTH
    -Wno-UNUSED
    -Wno-UNOPTFLAT
    -Wno-DECLFILENAME
    -Wno-PINCONNECTEMPTY
    -j 0
    --top-module ${ISA_TARGET}_system
    -CFLAGS "-Wno-unused-variable -Wno-bool-operation -Wno-parentheses-equality"
  PREFIX V${ISA_TARGET}_system
  TRACE_THREADS 2
  THREADS ${NUM_THREADS}
)

target_include_directories(demu PUBLIC
  $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
  $<INSTALL_INTERFACE:include>
  "${PROTO_OUT}"
  "${PROTO_OUT}/configs"
  "${PROTO_OUT}/isa"
)

set_target_properties(demu PROPERTIES
  ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
  LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
)

# spdlog
target_link_libraries(demu PUBLIC spdlog::spdlog)

# protobuf 
set(ABSL_LINK_TARGETS "")

foreach(_absl_target
  absl::base
  absl::strings
  absl::str_format
  absl::status
  absl::statusor
  absl::log
  absl::log_internal_message
  absl::log_internal_check_op
  absl::hash
  absl::flat_hash_map
  absl::flat_hash_set
  absl::cord
  absl::synchronization
  utf8_range
)
  if(TARGET ${_absl_target})
    list(APPEND ABSL_LINK_TARGETS ${_absl_target})
  endif()
endforeach()

# Fallback: some packaged protobuf builds require utf8_range but do not export a CMake target.
# When the target is missing, try to locate the static library manually and link it.
if(NOT TARGET utf8_range)
  find_library(UTF8_RANGE_LIB NAMES utf8_range)
  if(UTF8_RANGE_LIB)
    message(STATUS "Linking external utf8_range: ${UTF8_RANGE_LIB}")
    list(APPEND ABSL_LINK_TARGETS ${UTF8_RANGE_LIB})
  else()
    message(WARNING "utf8_range library not found; protobuf may fail to link if it was built with utf8_range")
  endif()
endif()

list(APPEND ABSL_LINK_TARGETS absl::status absl::strings)
target_link_libraries(demu PUBLIC
  protobuf::libprotobuf
  ${ABSL_LINK_TARGETS}
)

target_compile_definitions(demu PRIVATE
  RTL_DIR="${RTL_DIR}"
  RTL_CONFIG_FILE="${RTL_DIR}/config.json"
  RTL_CONFIG_BIN="${RTL_DIR}/config.pb"
)
