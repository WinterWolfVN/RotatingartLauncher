cmake_minimum_required(VERSION 3.22.1)
project(fmod_sdl C)

# Build the FMOD stub library (static, only needed at link time)
add_library(fmod_stub STATIC
    fmod_stub.c
)
target_include_directories(fmod_stub PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

# Build the FMOD_SDL library
add_library(fmod_SDL SHARED
    ${CMAKE_CURRENT_SOURCE_DIR}/../FMOD_SDL/FMOD_SDL.c
)
target_include_directories(fmod_SDL PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/../FMOD_SDL
)
target_link_libraries(fmod_SDL PRIVATE
    SDL2
    fmod_stub
)
target_compile_options(fmod_SDL PRIVATE
    -O3 -Wall -pedantic
)

