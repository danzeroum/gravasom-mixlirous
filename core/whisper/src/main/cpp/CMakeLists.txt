cmake_minimum_required(VERSION 3.22.1)
project("gravasom_whisper" LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)

# whisper.cpp (MIT) vem de WHISPER_SRC (core/whisper/scripts/buscar-whisper.sh, hash-pinned). Estatico: so o necessario entra na .so.
add_subdirectory(${WHISPER_SRC} whisper_build EXCLUDE_FROM_ALL)

add_library(gravasom_whisper SHARED whisper_jni.cpp)
target_include_directories(gravasom_whisper PRIVATE ${WHISPER_SRC}/include ${WHISPER_SRC}/ggml/include)
target_link_libraries(gravasom_whisper PRIVATE whisper log)
target_compile_options(gravasom_whisper PRIVATE -O3 -fvisibility=hidden)
target_link_options(gravasom_whisper PRIVATE -Wl,--gc-sections -Wl,-z,max-page-size=16384)
