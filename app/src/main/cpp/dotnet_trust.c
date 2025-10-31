/**
 * @file dotnet_trust.c
 * @brief 受信程序集列表构建器实现
 */

#include <dirent.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <sys/stat.h>
#include <android/log.h>
#include "dotnet_framework.h"

#define LOG_TAG "GameLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

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
 * @brief 检查文件是否存在
 * 
 * @param path 要检查的文件路径
 * @return 1 表示文件存在，0 表示不存在或不是普通文件
 */
static int file_exists(const char* path) {
    struct stat statbuf;
    return (stat(path, &statbuf) == 0 && S_ISREG(statbuf.st_mode));
}

/**
 * @brief 检查程序集名称是否已存在于列表中
 * 
 * @param assembly_name 要检查的程序集文件名（如 "System.dll"）
 * @param list 已存在的程序集路径列表（使用 : 分隔）
 * @return 1 表示程序集已存在，0 表示不存在
 * 
 * 此函数用于去重，确保同名程序集只添加一次。
 * 比较时仅使用文件名，忽略路径部分。
 */
static int is_assembly_in_list(const char* assembly_name, const char* list) {
    if (!list || !assembly_name) return 0;
    char* pos = (char*)list;
    
    // 遍历列表中的每个路径
    while (pos && *pos) {
        char* next_colon = strchr(pos, ':');
        char path_segment[512];
        
        // 提取当前路径段
        if (next_colon) {
            size_t len = (size_t)(next_colon - pos);
            if (len >= sizeof(path_segment)) len = sizeof(path_segment) - 1;
            strncpy(path_segment, pos, len);
            path_segment[len] = '\0';
            pos = next_colon + 1;
        } else {
            strncpy(path_segment, pos, sizeof(path_segment) - 1);
            path_segment[sizeof(path_segment) - 1] = '\0';
            pos = NULL;
        }
        
        // 提取文件名并比较
        char* filename = strrchr(path_segment, '/');
        if (filename && strcmp(filename + 1, assembly_name) == 0) return 1;
    }
    return 0;
}

/**
 * @brief 递归扫描目录查找 .dll 文件并添加到结果列表
 * 
 * @param directory 要扫描的目录路径
 * @param result 结果缓冲区（将追加找到的 DLL 路径）
 * @param result_size 结果缓冲区的大小
 * @param recursive 是否递归扫描子目录（1 表示递归，0 表示不递归）
 * 
 * 此函数会扫描指定目录中的所有 .dll 文件，并将它们的完整路径
 * 添加到结果字符串中（使用 : 分隔）。如果程序集已存在于列表中，
 * 则跳过（去重）。
 */
static void scan_directory_for_dlls(const char* directory, char* result, size_t result_size, int recursive) {
    DIR* dir;
    struct dirent* entry;
    if ((dir = opendir(directory)) == NULL) return;
    
    while ((entry = readdir(dir)) != NULL) {
        // 跳过 . 和 .. 目录
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;
        
        char full_path[1024];
        snprintf(full_path, sizeof(full_path), "%s/%s", directory, entry->d_name);
        
        // 检查是否为 .dll 文件
        if (strlen(entry->d_name) > 4 && strcmp(entry->d_name + strlen(entry->d_name) - 4, ".dll") == 0) {
            if (file_exists(full_path)) {
                // 去重检查：避免添加重复的程序集
                if (!is_assembly_in_list(entry->d_name, result)) {
                    if (strlen(result) > 0) strncat(result, ":", result_size - strlen(result) - 1);
                    strncat(result, full_path, result_size - strlen(result) - 1);
                    LOGI("Found DLL: %s", full_path);
                }
            }
        }
        
        // 如果启用递归，扫描子目录
        if (recursive) {
            struct stat s;
            if (stat(full_path, &s) == 0 && S_ISDIR(s.st_mode)) {
                scan_directory_for_dlls(full_path, result, result_size, recursive);
            }
        }
    }
    closedir(dir);
}

/**
 * @brief 构建受信程序集列表
 * 
 * @param appPath .NET 应用程序的主程序集路径（如 /path/to/MyApp.dll）
 * @param dotnetPath .NET 运行时根目录路径
 * @return 返回动态分配的程序集路径列表（使用 : 分隔）
 * 
 * 构建过程分为以下步骤：
 * 1. 加载所有框架程序集（最高优先级）
 * 2. 加载应用程序目录中的程序集（递归）
 * 3. 加载额外的库目录中的程序集
 * 
 * 所有程序集都会进行去重检查，确保同名程序集只加载一次。
 */
char* build_trusted_assemblies_list(const char* appPath, const char* dotnetPath) {
    LOGI("=== Building Trusted Assemblies List ===");
    
    // 分配 64KB 缓冲区用于存储程序集列表
    size_t buffer_size = 65536;
    char* trusted_assemblies = (char*)calloc(buffer_size, 1);
    if (!trusted_assemblies) {
        LOGE("Failed to allocate memory for trusted assemblies list");
        return NULL;
    }

    // 从应用程序路径提取目录路径
    char appDir[512];
    snprintf(appDir, sizeof(appDir), "%s", appPath);
    char* lastSlash = strrchr(appDir, '/');
    if (lastSlash) *lastSlash = '\0';

    LOGI("Application directory: %s", appDir);
    LOGI("Dotnet directory: %s", dotnetPath);

    // 选择框架版本
    char frameworkVersion[128] = {0};
    pick_framework_version(dotnetPath, frameworkVersion, sizeof(frameworkVersion));

    // 构建框架路径
    char frameworkPath[512];
    if (frameworkVersion[0] != '\0') 
        snprintf(frameworkPath, sizeof(frameworkPath), "%s/shared/Microsoft.NETCore.App/%s", dotnetPath, frameworkVersion);
    else 
        snprintf(frameworkPath, sizeof(frameworkPath), "%s/shared/Microsoft.NETCore.App", dotnetPath);

    // 步骤 1：加载所有框架程序集（最高优先级）
    if (directory_exists(frameworkPath)) {
        LOGI("=== Step 1: Loading ALL framework assemblies ===");
        DIR* dir = opendir(frameworkPath);
        if (dir) {
            struct dirent* entry;
            int framework_count = 0;
            while ((entry = readdir(dir)) != NULL) {
                // 检查是否为 .dll 文件
                if (strlen(entry->d_name) > 4 && strcmp(entry->d_name + strlen(entry->d_name) - 4, ".dll") == 0) {
                    char full_path[512];
                    snprintf(full_path, sizeof(full_path), "%s/%s", frameworkPath, entry->d_name);
                    if (file_exists(full_path)) {
                        if (strlen(trusted_assemblies) > 0) 
                            strncat(trusted_assemblies, ":", buffer_size - strlen(trusted_assemblies) - 1);
                        strncat(trusted_assemblies, full_path, buffer_size - strlen(trusted_assemblies) - 1);
                        framework_count++;
                    }
                }
            }
            closedir(dir);
            LOGI("Total framework assemblies loaded: %d", framework_count);
        }
    }

    // 步骤 2：加载应用程序程序集（递归扫描，排除框架重复项）
    if (directory_exists(appDir)) {
        LOGI("=== Step 2: Loading application assemblies (excluding framework duplicates) ===");
        scan_directory_for_dlls(appDir, trusted_assemblies, buffer_size, 1);
    }

    // 步骤 3：扫描额外的库目录
    const char* additional_dirs[] = { "/publish", "/libs", "/native", "/runtimes", NULL };
    for (int i = 0; additional_dirs[i] != NULL; i++) {
        char full_dir_path[512];
        
        // 扫描应用目录下的额外目录
        snprintf(full_dir_path, sizeof(full_dir_path), "%s%s", appDir, additional_dirs[i]);
        if (directory_exists(full_dir_path)) {
            scan_directory_for_dlls(full_dir_path, trusted_assemblies, buffer_size, 1);
        }
        
        // 扫描 .NET 运行时目录下的额外目录
        snprintf(full_dir_path, sizeof(full_dir_path), "%s%s", dotnetPath, additional_dirs[i]);
        if (directory_exists(full_dir_path)) {
            scan_directory_for_dlls(full_dir_path, trusted_assemblies, buffer_size, 1);
        }
    }

    return trusted_assemblies;
}


