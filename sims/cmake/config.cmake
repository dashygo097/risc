# config.cmake 

# compilation configurations
option(USE_CCACHE "Use ccache to speed up recompilation" ON)

# settings
set(ISA "rv32i")
set(RTL_DIR "${CMAKE_SOURCE_DIR}/../build")
option(ENABLE_SYSTEM "Enable system-level simulation" OFF)
option(ENABLE_TRACE "Enable VCD tracing" ON)
option(ENABLE_COVERAGE "Enable coverage collection" ON)
