cmake_minimum_required(VERSION 3.0)
project(danpatch)

# C++11 is required
set(CMAKE_CXX_FLAGS "-g -gdwarf-3 -Wall -Wno-unused-local-typedefs -std=c++0x")

add_subdirectory(src)
