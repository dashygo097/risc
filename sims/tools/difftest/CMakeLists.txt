set(REF_MODEL "nemu" CACHE STRING "Reference model for difftest (nemu, qemu, spike)")
set(DIFF_TARGET ${ISA_TARGET}_diff_${REF_MODEL})

add_executable(${DIFF_TARGET})

target_compile_definitions(${DIFF_TARGET} PRIVATE REF_MODEL_STR="${REF_MODEL}")

target_sources(${DIFF_TARGET} PRIVATE 
  main.cpp
)

target_link_libraries(${DIFF_TARGET} PRIVATE 
  demu 
  Threads::Threads 
  ${CMAKE_DL_LIBS} 
)

set_target_properties(${DIFF_TARGET} PROPERTIES
  OUTPUT_NAME demu_diff_${ISA_TARGET}_${REF_MODEL}
  RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin
)

install(TARGETS ${DIFF_TARGET} DESTINATION bin)
