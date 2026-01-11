add_executable(${ISA}_system_verilated)

# Simulator sources
target_sources(${ISA}_system_verilated PRIVATE main.cpp)

target_link_libraries(${ISA}_system_verilated PRIVATE demu Threads::Threads)

set_target_properties(${ISA}_system_verilated PROPERTIES
  OUTPUT_NAME demu_${ISA}_system
  RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin
)

add_executable(demu_${ISA}_system ALIAS ${ISA}_system_verilated)
install(TARGETS ${ISA}_system_verilated DESTINATION bin)
