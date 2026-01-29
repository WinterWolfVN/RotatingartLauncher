# RALCore SDL 补丁编译配置
#
# 使用方法:
# 1. 在主 CMakeLists.txt 中 add_subdirectory(SDL_patches)
# 2. 链接 ral_sdl_patches 库

cmake_minimum_required(VERSION 3.10)

project(ral_sdl_patches C)

# 包含 SDL 头文件
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../SDL/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../SDL/src)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../SDL/src/video)
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# 补丁源文件
set(RAL_PATCH_SOURCES
    # Android JNI 扩展
    # android/ral_android_jni.c
    
    # 输入扩展
    # input/ral_touch_multitouch.c
    # input/ral_mouse_range.c
    
    # 手柄扩展  
    # joystick/ral_joystick_rumble.c
    
    # 音频扩展
    # audio/ral_aaudio_config.c
)

# 补丁头文件
set(RAL_PATCH_HEADERS
    ral_sdl_config.h
    android/ral_android_jni.h
    android/ral_android_renderer.h
    input/ral_touch_multitouch.h
    input/ral_mouse_range.h
)

# 当前阶段：补丁代码直接嵌入 SDL 源码
# 未来阶段：分离为独立库
#
# add_library(ral_sdl_patches STATIC ${RAL_PATCH_SOURCES})
# target_include_directories(ral_sdl_patches PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

# 导出头文件目录供其他模块使用
set(RAL_SDL_PATCHES_INCLUDE_DIR ${CMAKE_CURRENT_SOURCE_DIR} PARENT_SCOPE)

message(STATUS "RALCore SDL Patches configured")
message(STATUS "  Headers: ${RAL_PATCH_HEADERS}")
