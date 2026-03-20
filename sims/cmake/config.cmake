# config.cmake 

# compilation configurations
option(USE_CCACHE "Use ccache to speed up recompilation" ON)

# settings
set(ISA_TARGET "rv32i")
set(RTL_DIR "${CMAKE_SOURCE_DIR}/../build")

# options
option(ENABLE_TRACE "Enable VCD tracing" ON)
option(ENABLE_COVERAGE "Enable coverage collection" ON)
