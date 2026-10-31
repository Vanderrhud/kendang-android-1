cmake_minimum_required(VERSION 3.22.1)
project(kendang_audio LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(kendang_audio SHARED
    native-lib.cpp
    AudioEngine.cpp
    Resampler.cpp
)

target_include_directories(kendang_audio PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(kendang_audio
    PRIVATE
    oboe::oboe
    log
)
