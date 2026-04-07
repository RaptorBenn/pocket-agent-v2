cmake_minimum_required(VERSION 3.22.1)

project("pocket-stt" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Point to whisper.cpp submodule
set(WHISPER_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/whisper.cpp)

# Force static linking — prevents ggml .so collision with llama.cpp module
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
add_subdirectory(${WHISPER_SRC} build-whisper EXCLUDE_FROM_ALL)

# Build our JNI bridge
add_library(${CMAKE_PROJECT_NAME} SHARED whisper_jni.c)

target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE -fvisibility=hidden)
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE -Wl,--exclude-libs,ALL)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${WHISPER_SRC}/include)

target_link_libraries(${CMAKE_PROJECT_NAME}
    whisper
    android
    log)
