set(DEMU_DBG_TARGET demu-${TARGET_ARCH}-dbg)
add_executable(${DEMU_DBG_TARGET})

target_sources(${DEMU_DBG_TARGET} PRIVATE
  main.cpp
  debugger.cpp
  breakpoint.cpp
)
target_link_libraries(${DEMU_DBG_TARGET} PRIVATE
  demu
  ${READLINE_LIB}
  Threads::Threads
)
set_target_properties(${DEMU_DBG_TARGET} PROPERTIES
  OUTPUT_NAME ${DEMU_DBG_TARGET}
  RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin
)

install(TARGETS ${DEMU_DBG_TARGET} DESTINATION bin)
