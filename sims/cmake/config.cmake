# config.cmake 

# compilation configurations
option(USE_CCACHE "Use ccache to speed up recompilation" ON)

# settings
set(ISA "rv32i")
option(ENABLE_TRACE "Enable VCD tracing" ON)
option(ENABLE_COVERAGE "Enable coverage collection" ON)
