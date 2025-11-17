add_library(rv32_cpu_verilated INTERFACE)

verilate(rv32_cpu_verilated
    SOURCES ${RTL_SOURCES}
    TRACE_THREADS 2
    VERILATOR_ARGS 
        --cc
        -Wall
        -Wno-WIDTH
        -Wno-UNUSED
        -Wno-UNOPTFLAT
        -Wno-DECLFILENAME
        --top-module rv32_cpu
    PREFIX Vrv32_cpu
)

if(ENABLE_TRACE)
    verilate(rv32_cpu_verilated TRACE)
endif()

set(SIMULATOR_SOURCES
    main.cc
    cpu_sim.cc
    memory.cc
    instruction.cc
    elf_loader.cc
    hex_loader.cc
    trace.cc
)

add_executable(rv32_simulator ${SIMULATOR_SOURCES})

target_link_libraries(rv32_simulator PRIVATE rv32_cpu_verilated)

target_include_directories(rv32_simulator PRIVATE
    ${CMAKE_SOURCE_DIR}/include
    ${CMAKE_BINARY_DIR}
)

find_package(Threads REQUIRED)
target_link_libraries(rv32_simulator PRIVATE Threads::Threads)

set_target_properties(rv32_simulator PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin
)
