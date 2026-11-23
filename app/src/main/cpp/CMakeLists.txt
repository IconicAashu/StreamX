# CMakeLists.txt for STREAMX Native P2P Streaming Engine
cmake_minimum_required(VERSION 3.22.1)

project("streamx_p2p" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Compiler flags for security, performance, and optimization
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -fexceptions -frtti -fvisibility=hidden -O3")

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Core native P2P engine library
add_library(
    streamx_p2p
    SHARED
    p2p_streaming_engine.cpp
    p2p_jni.cpp
)

# Android system libraries
find_library(log-lib log)

target_link_libraries(
    streamx_p2p
    ${log-lib}
)
