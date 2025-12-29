add_executable(rv32i_cpu_verilated)

verilate(rv32i_cpu_verilated
  SOURCES ${RTL_SOURCES}
  VERILATOR_ARGS 
    -Wall
    -Wno-WIDTH
    -Wno-UNUSED
    -Wno-UNOPTFLAT
    -Wno-DECLFILENAME
    --top-module rv32i_cpu
  PREFIX Vrv32i_cpu
  TRACE_THREADS 2
)

# Simulator sources
set(SIMULATOR_SOURCES
  main.cc
  cpu_sim.cc
  memory.cc
  instruction.cc
  elf_loader.cc
  trace.cc
)

target_sources(rv32i_cpu_verilated PRIVATE ${SIMULATOR_SOURCES})

target_include_directories(rv32i_cpu_verilated PRIVATE
  ${CMAKE_SOURCE_DIR}/include
)

find_package(Threads REQUIRED)
target_link_libraries(rv32i_cpu_verilated PRIVATE Threads::Threads)

set_target_properties(rv32i_cpu_verilated PROPERTIES
  OUTPUT_NAME rv32i_simulator
  RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin
)

add_executable(rv32i_simulator ALIAS rv32i_cpu_verilated)
