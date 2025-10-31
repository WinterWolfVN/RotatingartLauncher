/**
 * @file dotnet_paths.h
 * @brief 原生库搜索路径构建器
 * 
 * 此模块负责构建 .NET 应用程序运行时所需的原生库搜索路径，
 * 包括应用目录、.NET 运行时目录和系统库目录。
 */
#pragma once

/**
 * @brief 构建原生 DLL 搜索路径
 * 
 * @param dotnetPath .NET 运行时根目录路径
 * @param appDir 应用程序目录路径
 * @return 返回动态分配的搜索路径字符串（使用 : 分隔），调用者需要 free
 * 
 * 此函数构建一个包含以下目录的搜索路径（如果存在）：
 * 1. 应用程序目录（appDir）
 * 2. .NET 运行时根目录
 * 3. Microsoft.NETCore.App 框架目录（特定版本或通用）
 * 4. Android 原生库目录（lib/android/arm64-v8a）
 * 5. 通用库目录（lib）
 * 6. 系统库目录（/system/lib64, /vendor/lib64）
 * 
 * @note 返回的字符串需要调用者使用 free 释放
 */
char* build_native_search_paths(const char* dotnetPath, const char* appDir);


