/**
 * @file sdl_entry.c
 * @brief SDL 入口点实现
 * 
 * 此文件提供了 SDL_main 入口点，作为 .NET 应用程序启动的桥梁。
 * SDL_main 由 SDLActivity（Java层）通过 native 方法调用，
 * 然后转发到 CoreCLR 启动逻辑。
 */

#include <jni.h>
#include <android/log.h>
#include "dotnet_host.h"

#define LOG_TAG "GameLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

/**
 * @brief JNI_OnLoad 回调
 * 
 * @param vm JavaVM 指针
 * @param reserved 保留参数（未使用）
 * @return JNI 版本号
 * 
 * SDL 库加载时的初始化回调。
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)vm;
    (void)reserved;
    return JNI_VERSION_1_6;
}

/**
 * @brief JNI_OnUnload 回调
 * 
 * @param vm JavaVM 指针
 * @param reserved 保留参数（未使用）
 * 
 * SDL 库卸载时的清理回调。
 */
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    (void)vm;
    (void)reserved;
}

/**
 * @brief SDL 主函数入口点
 * 
 * @param argc 参数数量（未使用）
 * @param argv 参数数组（未使用）
 * @return 应用程序退出码
 * 
 * 这是 .NET 应用程序的主入口点。所有启动参数已经由 Java 层
 * 通过 JNI 调用预先设置（见 GameLauncher.setLaunchParamsFull），
 * 因此这里直接调用 CoreCLR 启动函数。
 * 
 * 调用流程：
 * 1. Java SDLActivity 启动
 * 2. Java 调用 GameLauncher.setLaunchParamsFull() 设置参数
 * 3. SDL 调用此 SDL_main 函数
 * 4. 此函数调用 launch_with_coreclr_passthrough()
 * 5. CoreCLR 加载并执行 .NET 应用程序
 */
__attribute__((visibility("default"))) int SDL_main(int argc, char* argv[]) {
    (void)argc;
    (void)argv;
    
    LOGI("SDL_main started (all params provided by Java layer)");
    
    // 启动 CoreCLR 并执行 .NET 应用程序
    int result = launch_with_coreclr_passthrough();
    
    LOGI("CoreCLR execution finished with result: %d", result);
    return result;
}


