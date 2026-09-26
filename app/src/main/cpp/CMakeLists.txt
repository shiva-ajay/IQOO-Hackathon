cmake_minimum_required(VERSION 3.22.1)
project(fixlensllm CXX)
set(CMAKE_CXX_STANDARD 17)

# MNN headers come from the donor clone; libMNN.so is the prebuilt arm64 build (see docs/mnn-donor-notes.md).
set(MNN_ROOT "/home/shiva-ajay/3D/MNN" CACHE PATH "MNN source tree")
include_directories(${MNN_ROOT}/include ${MNN_ROOT}/transformers/llm/engine/include)

add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libMNN.so)

add_library(fixlensllm SHARED fixlens_llm.cpp)
target_link_libraries(fixlensllm MNN android log)
target_link_options(fixlensllm PRIVATE "-Wl,-z,max-page-size=16384")
