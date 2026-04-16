# config.cmake 

# compilation configurations
option(USE_CCACHE "Use ccache to speed up recompilation" ON)

# settings
set(ISA_TARGET "rv32im")
set(RTL_DIR "${CMAKE_SOURCE_DIR}/../build")

# options
set(NUM_THREADS 1)
set(NUM_TRACE_THREADS 2)
option(ENABLE_TRACE "Enable VCD tracing" ON)
option(ENABLE_COVERAGE "Enable coverage collection" ON)
