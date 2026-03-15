find_package(Threads REQUIRED)
find_package(spdlog CONFIG REQUIRED)
find_package(verilator HINTS $ENV{VERILATOR_ROOT})
find_package(Protobuf REQUIRED)
find_package(absl REQUIRED)

if(NOT verilator_FOUND)
  message(FATAL_ERROR "Verilator not found. Please install Verilator or set VERILATOR_ROOT")
else()
  set(VERILATOR_ARGS
    -Wall
    -Wno-WIDTH
    -Wno-UNUSED
    -Wno-UNOPTFLAT
    -Wno-DECLFILENAME
    -Wno-PINCONNECTEMPTY
  )

  if(${ISA_TARGET} STREQUAL "rv32i")
    set(__ISA_RV32I__ TRUE CACHE INTERNAL "rv32i is available")
    add_compile_definitions(__ISA_RV32I__)
  else()
    message(FATAL_ERROR "Unsupported ISA: ${ISA_TARGET}. Supported ISAs: rv32i")
  endif()

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

endif()

get_filename_component(PROTO_ROOT
  "${CMAKE_CURRENT_SOURCE_DIR}/../protos"
  ABSOLUTE
)
set(PROTO_OUT "${CMAKE_BINARY_DIR}/generated")
file(MAKE_DIRECTORY "${PROTO_OUT}")

file(GLOB PROTO_FILES
  CONFIGURE_DEPENDS
  "${PROTO_ROOT}/isa/*.proto"
  "${PROTO_ROOT}/configs/*.proto"
)

if(NOT PROTO_FILES)
  message(FATAL_ERROR "[proto] No .proto files found in ${PROTO_ROOT}")
endif()

file(MAKE_DIRECTORY "${PROTO_OUT}/configs")
file(MAKE_DIRECTORY "${PROTO_OUT}/isa")

set(PROTO_GENERATED_HEADERS "")
set(PROTO_GENERATED_SOURCES "")

foreach(PROTO_FILE ${PROTO_FILES})
  file(RELATIVE_PATH PROTO_REL "${PROTO_ROOT}" "${PROTO_FILE}")
  get_filename_component(PROTO_REL_DIR  "${PROTO_REL}" DIRECTORY)
  get_filename_component(PROTO_NAME     "${PROTO_FILE}" NAME_WE)
  list(APPEND PROTO_GENERATED_HEADERS "${PROTO_OUT}/${PROTO_REL_DIR}/${PROTO_NAME}.pb.h")
  list(APPEND PROTO_GENERATED_SOURCES "${PROTO_OUT}/${PROTO_REL_DIR}/${PROTO_NAME}.pb.cc")
endforeach()

add_custom_command(
  OUTPUT  ${PROTO_GENERATED_HEADERS} ${PROTO_GENERATED_SOURCES}
  COMMAND ${Protobuf_PROTOC_EXECUTABLE}
          --cpp_out=${PROTO_OUT}
          --proto_path=${PROTO_ROOT}
          ${PROTO_FILES}
  DEPENDS ${PROTO_FILES}
  COMMENT "[protoc] Generating pb files from ${PROTO_ROOT}/*.proto"
)

add_custom_target(gen_proto DEPENDS
  ${PROTO_GENERATED_HEADERS}
  ${PROTO_GENERATED_SOURCES}
)

set(PROTO_GENERATED_SOURCES "${PROTO_GENERATED_SOURCES}" CACHE INTERNAL "")
