cmake_minimum_required(VERSION 3.22.1)
project(fixlensllm C CXX)
set(CMAKE_CXX_STANDARD 17)

# MNN headers come from the donor clone; libMNN.so is the prebuilt arm64 build (see docs/mnn-donor-notes.md).
set(MNN_ROOT "/home/shiva-ajay/3D/MNN" CACHE PATH "MNN source tree")

add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libMNN.so)

add_library(fixlensllm SHARED fixlens_llm.cpp)
target_include_directories(fixlensllm PRIVATE ${MNN_ROOT}/include ${MNN_ROOT}/transformers/llm/engine/include)
target_link_libraries(fixlensllm MNN android log)
target_link_options(fixlensllm PRIVATE "-Wl,-z,max-page-size=16384")

# IR remote: the vendored IRext AC decoder (irext/README.md) + its JNI bridge.
file(GLOB IREXT_SOURCES ${CMAKE_SOURCE_DIR}/irext/*.c)
add_library(fixlensir SHARED fixlens_ir.cpp ${IREXT_SOURCES})
target_include_directories(fixlensir PRIVATE ${CMAKE_SOURCE_DIR}/irext/include)
target_compile_definitions(fixlensir PRIVATE BOARD_ANDROID)
target_compile_options(fixlensir PRIVATE $<$<COMPILE_LANGUAGE:C>:-w>)
target_link_libraries(fixlensir log)
target_link_options(fixlensir PRIVATE "-Wl,-z,max-page-size=16384")
