add_executable(${ISA_TARGET}_verilated)

target_sources(${ISA_TARGET}_verilated PRIVATE main.cpp)

target_link_libraries(${ISA_TARGET}_verilated PRIVATE demu Threads::Threads)

set_target_properties(${ISA_TARGET}_verilated PROPERTIES
  OUTPUT_NAME demu_${ISA_TARGET}
  RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin
)

add_executable(demu_${ISA_TARGET}_simulator ALIAS ${ISA_TARGET}_verilated)
install(TARGETS ${ISA_TARGET}_verilated DESTINATION bin)
