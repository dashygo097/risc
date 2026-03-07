# demu
add_library(demu ${DEMU_SOURCES} ${DEMU_HEADERS})

verilate(demu
  SOURCES ${CPU_RTL_SOURCE}
  VERILATOR_ARGS
    -Wall
    -Wno-WIDTH
    -Wno-UNUSED
    -Wno-UNOPTFLAT
    -Wno-DECLFILENAME
    -Wno-PINCONNECTEMPTY
    --top-module ${ISA}_cpu
  PREFIX V${ISA}_cpu
  TRACE_THREADS 2
)

if(ENABLE_SYSTEM)
  verilate(demu
    SOURCES ${SYSTEM_RTL_SOURCE}
    VERILATOR_ARGS
      -Wall
      -Wno-WIDTH
      -Wno-UNUSED
      -Wno-UNOPTFLAT
      -Wno-DECLFILENAME
      -Wno-PINCONNECTEMPTY
      --top-module ${ISA}_system
    PREFIX V${ISA}_system
    TRACE_THREADS 2
  )
endif()

target_include_directories(demu PUBLIC 
  $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
  $<INSTALL_INTERFACE:include>
)

set_target_properties(demu PROPERTIES
  ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
  LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
)

target_link_libraries(demu PUBLIC spdlog::spdlog)
target_link_libraries(demu PUBLIC nlohmann_json::nlohmann_json)

if(NOT DEFINED RTL_CONFIG_FILE)
  set(RTL_CONFIG_FILE "${RTL_DIR}/config.json")
endif()

if(NOT EXISTS "${RTL_CONFIG_FILE}")
  message(FATAL_ERROR 
    "[demu] config.json not found at: ${RTL_CONFIG_FILE}\n"
    "  Run 'sbt run' to generate it first, or set -DRTL_CONFIG_FILE=<path>"
  )
endif()

target_compile_definitions(demu PRIVATE
  RTL_DIR="${RTL_DIR}"
  RTL_CONFIG_FILE="${RTL_CONFIG_FILE}"
)

set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${RTL_CONFIG_FILE}")
