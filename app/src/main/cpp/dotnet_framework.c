/**
 * @file dotnet_framework.c
 * @brief .NET 框架版本选择器实现
 */

#include <dirent.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include "dotnet_params.h"
#include <android/log.h>

#define LOG_TAG "GameLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

/**
 * @brief 选择 Microsoft.NETCore.App 的最佳版本
 * 
 * @param dotnetPath .NET 运行时根目录路径
 * @param outVersion 输出缓冲区，用于存储选中的版本号
 * @param outSize 输出缓冲区的大小
 * 
 * 版本选择算法：
 * 1. 扫描 dotnetPath/shared/Microsoft.NETCore.App 目录
 * 2. 检查是否指定了主版本号（从 g_frameworkVersion 或环境变量）
 * 3. 如果指定了主版本，只选择该主版本内的最高版本
 * 4. 如果未指定，选择所有版本中的最高版本
 * 5. 版本比较按 major.minor.patch 逐级比较
 */
void pick_framework_version(const char* dotnetPath, char* outVersion, size_t outSize) {
    // 构建框架根目录路径
    char fxRoot[1024];
    snprintf(fxRoot, sizeof(fxRoot), "%s/shared/Microsoft.NETCore.App", dotnetPath);
    DIR* dir = opendir(fxRoot);
    if (!dir) { outVersion[0] = '\0'; return; }

    // 检查是否指定了首选主版本号
    int preferredMajor = -1;
    if (g_frameworkVersion && strlen(g_frameworkVersion) > 0) {
        // 从全局参数获取主版本号（如 "8" 表示只使用 .NET 8.x.x）
        preferredMajor = atoi(g_frameworkVersion);
    } else {
        // 从环境变量获取主版本号作为备选
        const char* envFx = getenv("DOTNET_FRAMEWORK_VERSION");
        if (envFx && *envFx) preferredMajor = atoi(envFx);
    }

    // 保存当前找到的最佳版本
    char best[128] = {0};
    int bestMajor = -1, bestMinor = -1, bestPatch = -1;

    // 遍历所有版本目录
    struct dirent* entry;
    while ((entry = readdir(dir)) != NULL) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;
        
        // 解析版本号（格式：major.minor.patch）
        int maj = -1, min = -1, pat = -1;
        if (sscanf(entry->d_name, "%d.%d.%d", &maj, &min, &pat) >= 2) {
            // 如果指定了主版本，跳过不匹配的版本
            if (preferredMajor != -1 && maj != preferredMajor) continue;
            
            // 比较版本号：优先比较主版本，然后次版本，最后修订版本
            if (maj > bestMajor || 
                (maj == bestMajor && (min > bestMinor || 
                (min == bestMinor && pat > bestPatch)))) {
                strncpy(best, entry->d_name, sizeof(best) - 1);
                bestMajor = maj; bestMinor = min; bestPatch = pat;
            }
        }
    }
    closedir(dir);

    // 输出结果
    if (best[0] != '\0') {
        strncpy(outVersion, best, outSize - 1);
        outVersion[outSize - 1] = '\0';
        LOGI("Picked framework version: %s", outVersion);
    } else {
        outVersion[0] = '\0';
        LOGI("No framework version picked; fallback.");
    }
}


