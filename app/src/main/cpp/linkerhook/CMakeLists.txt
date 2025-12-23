cmake_minimum_required(VERSION 3.18.1)

project(linkerhook)

add_library(linkerhook SHARED
    linkerhook.c
)

# Include liblinkernsbypass headers for android_dlextinfo
target_include_directories(linkerhook PRIVATE
    ${CMAKE_SOURCE_DIR}/liblinkernsbypass
)

target_link_libraries(linkerhook
    android
    log
    dl
)


