cmake_minimum_required(VERSION 3.21)
project(demu LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Apps
add_subdirectory(cpu)
if(ENABLE_SYSTEM)
  add_subdirectory(system)
endif()

