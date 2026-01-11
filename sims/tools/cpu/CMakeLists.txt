add_executable(${ISA}_cpu_verilated)

# Simulator sources
target_sources(${ISA}_cpu_verilated PRIVATE main.cpp)

target_link_libraries(${ISA}_cpu_verilated PRIVATE demu Threads::Threads)

set_target_properties(${ISA}_cpu_verilated PROPERTIES
  OUTPUT_NAME demu_${ISA}_cpu
  RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin
)

add_executable(demu_${ISA}_cpu ALIAS ${ISA}_cpu_verilated)
install(TARGETS ${ISA}_cpu_verilated DESTINATION bin)
