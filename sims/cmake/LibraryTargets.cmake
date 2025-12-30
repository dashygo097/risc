# rvsim
add_library(rvsim ${RVSIM_SOURCES} ${RVSIM_HEADERS})

target_include_directories(rvsim PUBLIC 
  $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
  $<INSTALL_INTERFACE:include>
)

# 3rdparty libs to link statically
# target_link_libraries(rvsim PUBLIC nlohmann_json::nlohmann_json)

set_target_properties(rvsim PROPERTIES
  ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
  LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
)
