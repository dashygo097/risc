add_executable(${ISA_TARGET}_cpu_verilated)

# Simulator sources
target_sources(${ISA_TARGET}_cpu_verilated PRIVATE main.cpp)

target_link_libraries(${ISA_TARGET}_cpu_verilated PRIVATE demu Threads::Threads)

set_target_properties(${ISA_TARGET}_cpu_verilated PROPERTIES
  OUTPUT_NAME demu_${ISA_TARGET}_cpu
  RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin
)

add_executable(demu_${ISA_TARGET}_cpu ALIAS ${ISA_TARGET}_cpu_verilated)
install(TARGETS ${ISA_TARGET}_cpu_verilated DESTINATION bin)
