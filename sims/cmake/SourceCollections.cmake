# Collect source and header files

file(GLOB_RECURSE DEMU_SOURCES 
  ${CMAKE_CURRENT_SOURCE_DIR}/lib/*.c 
  ${CMAKE_CURRENT_SOURCE_DIR}/lib/*.cc 
  ${CMAKE_CURRENT_SOURCE_DIR}/lib/*.cpp
)

file(GLOB_RECURSE DEMU_HEADERS 
  ${CMAKE_CURRENT_SOURCE_DIR}/include/*.h 
  ${CMAKE_CURRENT_SOURCE_DIR}/include/*.hh 
  ${CMAKE_CURRENT_SOURCE_DIR}/include/*.hpp
)

set(RTL_SOURCE ${RTL_DIR}/${ISA_TARGET}_system.sv)
if(NOT EXISTS ${RTL_SOURCE})
  message(WARNING "RTL file not found: ${RTL_SOURCE}")
  message(WARNING "Please make sure your Chisel design has been generated")
endif()
