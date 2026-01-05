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
    --top-module ${ISA}_cpu
  PREFIX V${ISA}_cpu
  TRACE_THREADS 2
)

target_include_directories(demu PUBLIC 
  $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
  $<INSTALL_INTERFACE:include>
)

set_target_properties(demu PROPERTIES
  ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
  LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
)
