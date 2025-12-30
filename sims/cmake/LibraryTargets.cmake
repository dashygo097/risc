# demu
add_library(demu ${DEMU_SOURCES} ${DEMU_HEADERS})

target_include_directories(demu PUBLIC 
  $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
  $<INSTALL_INTERFACE:include>
)

# 3rdparty libs to link statically
# target_link_libraries(demu PRIVATE nlohmann_json::nlohmann_json)

set_target_properties(demu PROPERTIES
  ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
  LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
)
