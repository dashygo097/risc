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
    --top-module ${ISA_TARGET}_system
    -CFLAGS "-Wno-unused-variable -Wno-bool-operation -Wno-parentheses-equality"
  PREFIX V${ISA_TARGET}_system
  TRACE_THREADS 2
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

target_link_libraries(demu PUBLIC spdlog::spdlog)
target_link_libraries(demu PUBLIC
  protobuf::libprotobuf
  absl::log
  absl::log_internal_message
  absl::log_internal_check_op
  absl::status
  absl::strings
)

target_compile_definitions(demu PRIVATE
  RTL_DIR="${RTL_DIR}"
  RTL_CONFIG_FILE="${RTL_DIR}/config.json"
  RTL_CONFIG_BIN="${RTL_DIR}/config.pb"
)
