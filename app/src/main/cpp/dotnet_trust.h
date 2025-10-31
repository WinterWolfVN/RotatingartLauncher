/**
 * @file dotnet_trust.h
 * @brief 受信程序集列表构建器
 * 
 * 此模块负责构建 CoreCLR 运行时所需的 TRUSTED_PLATFORM_ASSEMBLIES (TPA) 列表。
 * TPA 列表包含所有应被信任并可以加载的 .NET 程序集（.dll 文件）。
 */
#pragma once

/**
 * @brief 构建 CoreCLR 所需的 TRUSTED_PLATFORM_ASSEMBLIES 列表
 * 
 * @param appPath .NET 应用程序的主程序集路径
 * @param dotnetPath .NET 运行时根目录路径
 * @return 返回动态分配的程序集路径列表（使用 : 分隔），调用者需要 free
 * 
 * 此函数按以下顺序扫描并添加程序集：
 * 1. 框架程序集（来自 shared/Microsoft.NETCore.App/版本号/）- 最高优先级
 * 2. 应用程序程序集（来自应用目录及其子目录）
 * 3. 额外的库目录（publish, libs, native, runtimes）
 * 
 * 程序集去重规则：如果同名程序集已存在于列表中，则跳过后续同名程序集。
 * 这确保了框架程序集优先于应用程序自带的同名程序集。
 * 
 * @note 返回的字符串需要调用者使用 free 释放
 * @note 如果内存分配失败，返回 NULL
 */
char* build_trusted_assemblies_list(const char* appPath, const char* dotnetPath);


