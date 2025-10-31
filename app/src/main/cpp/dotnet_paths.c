/**
 * @file dotnet_paths.c
 * @brief 原生库搜索路径构建器实现
 */

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <sys/stat.h>
#include "dotnet_params.h"
#include <android/log.h>

#define LOG_TAG "GameLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

/**
 * @brief 检查目录是否存在
 * 
 * @param path 要检查的目录路径
 * @return 1 表示目录存在，0 表示不存在或不是目录
 */
static int directory_exists(const char* path) {
    struct stat statbuf;
    return (stat(path, &statbuf) == 0 && S_ISDIR(statbuf.st_mode));
}

/**
 * @brief 构建原生 DLL 搜索路径
 * 
 * @param dotnetPath .NET 运行时根目录路径
 * @param appDir 应用程序目录路径
 * @return 返回动态分配的搜索路径字符串（使用 : 分隔）
 * 
 * 此函数按优先级顺序构建搜索路径：
 * 1. 应用程序目录（最高优先级，包含应用特定的原生库）
 * 2. .NET 运行时目录及其子目录
 * 3. 系统库目录（最低优先级）
 */
char* build_native_search_paths(const char* dotnetPath, const char* appDir) {
    // 分配路径缓冲区（4KB 应该足够容纳所有路径）
    size_t buffer_size = 4096;
    char* search_paths = (char*)calloc(buffer_size, 1);
    if (!search_paths) return NULL;

    // 1. 添加应用程序目录（最高优先级）
    if (directory_exists(appDir)) {
        strncat(search_paths, appDir, buffer_size - strlen(search_paths) - 1);
        LOGI("Added native search path: %s", appDir);
    }

    // 获取指定的框架版本（如果有）
    const char* frameworkVersion = g_frameworkVersion;

    // 2. 添加 .NET 运行时相关目录
    const char* paths[] = {
            dotnetPath,                             // .NET 根目录
            "/shared/Microsoft.NETCore.App",        // 框架共享目录
            "/lib/android/arm64-v8a",               // Android ARM64 原生库
            "/lib",                                 // 通用库目录
            NULL
    };

    for (int i = 0; paths[i] != NULL; i++) {
        char full_path[512];
        
        // 处理相对路径和绝对路径
        if (paths[i][0] == '/') {
            // 如果是框架目录且指定了版本，使用特定版本路径
            if (strcmp(paths[i], "/shared/Microsoft.NETCore.App") == 0 && 
                frameworkVersion && strlen(frameworkVersion) > 0) {
                snprintf(full_path, sizeof(full_path), "%s/shared/Microsoft.NETCore.App/%s", 
                         dotnetPath, frameworkVersion);
            } else {
                // 拼接 dotnetPath 和相对路径
                snprintf(full_path, sizeof(full_path), "%s%s", dotnetPath, paths[i]);
            }
        } else {
            // 绝对路径直接使用
            snprintf(full_path, sizeof(full_path), "%s", paths[i]);
        }

        // 仅添加存在的目录
        if (directory_exists(full_path)) {
            if (strlen(search_paths) > 0) {
                strncat(search_paths, ":", buffer_size - strlen(search_paths) - 1);
            }
            strncat(search_paths, full_path, buffer_size - strlen(search_paths) - 1);
            LOGI("Added native search path: %s", full_path);
        }
    }

    // 3. 添加 Android 系统库目录（最低优先级）
    const char* system_lib_paths[] = {
            "/system/lib64",    // Android 系统库
            "/vendor/lib64",    // 厂商库
            NULL
    };
    for (int i = 0; system_lib_paths[i] != NULL; i++) {
        if (directory_exists(system_lib_paths[i])) {
            if (strlen(search_paths) > 0) {
                strncat(search_paths, ":", buffer_size - strlen(search_paths) - 1);
            }
            strncat(search_paths, system_lib_paths[i], buffer_size - strlen(search_paths) - 1);
            LOGI("Added system library path: %s", system_lib_paths[i]);
        }
    }

    return search_paths;
}


