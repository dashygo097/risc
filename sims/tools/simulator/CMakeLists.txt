set(DEMU_SIM_TARGET demu-${TARGET_ARCH})
add_executable(${DEMU_SIM_TARGET})

target_sources(${DEMU_SIM_TARGET} PRIVATE main.cpp)
target_link_libraries(${DEMU_SIM_TARGET} PRIVATE demu Threads::Threads)
set_target_properties(${DEMU_SIM_TARGET} PROPERTIES
  OUTPUT_NAME ${DEMU_SIM_TARGET} 
  RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin
)

install(TARGETS ${DEMU_SIM_TARGET} DESTINATION bin)
