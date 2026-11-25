cmake_minimum_required(VERSION 3.22.1)
project("fieldflow_whisper")

# Point WHISPER_CPP_DIR to your local whisper.cpp checkout or submodule
set(WHISPER_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp" CACHE PATH "Path to whisper.cpp")

if(EXISTS "${WHISPER_CPP_DIR}/CMakeLists.txt")
    set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    add_subdirectory("${WHISPER_CPP_DIR}" whisper_build)
    set(HAS_WHISPER TRUE)
else()
    message(WARNING "whisper.cpp not found at ${WHISPER_CPP_DIR}; building stub JNI.")
    set(HAS_WHISPER FALSE)
endif()

add_library(fieldflow_whisper SHARED whisper_jni.cpp)

if(HAS_WHISPER)
    target_compile_definitions(fieldflow_whisper PRIVATE HAS_WHISPER_CPP=1)
    target_include_directories(fieldflow_whisper PRIVATE "${WHISPER_CPP_DIR}/include" "${WHISPER_CPP_DIR}/ggml/include")
    target_link_libraries(fieldflow_whisper PRIVATE whisper android log)
else()
    target_link_libraries(fieldflow_whisper PRIVATE android log)
endif()
