/**
 * @file dotnet_framework.h
 * @brief .NET 框架版本选择器
 * 
 * 此模块负责从已安装的 .NET 运行时中选择最合适的框架版本。
 * 支持自动选择最高版本，也支持按主版本号（如 7、8、9）过滤。
 */
#pragma once

/**
 * @brief 选择 Microsoft.NETCore.App 的最佳版本
 * 
 * @param dotnetPath .NET 运行时根目录路径
 * @param outVersion 输出缓冲区，用于存储选中的版本号（如 "8.0.18"）
 * @param outSize 输出缓冲区的大小
 * 
 * 此函数扫描 dotnetPath/shared/Microsoft.NETCore.App 目录下的所有版本，
 * 并选择最高的可用版本。如果全局变量 g_frameworkVersion 或环境变量
 * DOTNET_FRAMEWORK_VERSION 指定了主版本号（如 "7"、"8"），则只在该
 * 主版本内选择。
 * 
 * 版本比较规则：先比较主版本号，再比较次版本号，最后比较修订版本号。
 * 
 * @note 如果找不到匹配的版本，outVersion[0] 将被设置为 '\0'
 */
void pick_framework_version(const char* dotnetPath, char* outVersion, size_t outSize);


