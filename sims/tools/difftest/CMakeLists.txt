set(DIFF_TARGET ${ISA_TARGET}_diff)

add_executable(${DIFF_TARGET})

target_sources(${DIFF_TARGET} PRIVATE 
  main.cpp
)

target_link_libraries(${DIFF_TARGET} PRIVATE 
  demu 
  Threads::Threads 
  ${CMAKE_DL_LIBS} 
)

set_target_properties(${DIFF_TARGET} PROPERTIES
  OUTPUT_NAME demu_${ISA_TARGET}_diff
  RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin
)

install(TARGETS ${DIFF_TARGET} DESTINATION bin)
