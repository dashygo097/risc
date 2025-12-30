# vkr
add_library(vkr ${VKR_SOURCES} ${VKR_HEADERS})

target_include_directories(vkr PUBLIC 
  $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
  $<INSTALL_INTERFACE:include>
)

# Vulkan
target_include_directories(vkr PUBLIC ${Vulkan_INCLUDE_DIRS})

# 3rdparty libs to link statically
target_link_libraries(vkr PUBLIC glfw)
target_link_libraries(vkr PUBLIC Vulkan::Vulkan)
target_link_libraries(vkr PUBLIC glm::glm-header-only)
target_link_libraries(vkr PUBLIC imgui)
target_link_libraries(vkr PUBLIC tinyobjloader)
target_link_libraries(vkr PUBLIC stb)
target_link_libraries(vkr PUBLIC nlohmann_json::nlohmann_json)

if (APPLE) 
  target_link_libraries(vkr PUBLIC
    "-framework Metal"
    "-framework Foundation"
    "-framework IOSurface"
  )
endif()

set_target_properties(vkr PROPERTIES
  ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
  LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
)
