set(DEMU_DIFF_TARGET demu-${TARGET_ARCH}-diff)
add_executable(${DEMU_DIFF_TARGET})

target_sources(${DEMU_DIFF_TARGET} PRIVATE 
  main.cpp
)
target_link_libraries(${DEMU_DIFF_TARGET} PRIVATE 
  demu 
  Threads::Threads 
  ${CMAKE_DL_LIBS} 
)
set_target_properties(${DEMU_DIFF_TARGET} PROPERTIES
  OUTPUT_NAME ${DEMU_DIFF_TARGET} 
  RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin
)

install(TARGETS ${DEMU_DIFF_TARGET} DESTINATION bin)
