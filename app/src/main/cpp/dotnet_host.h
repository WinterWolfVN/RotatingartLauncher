/**
 * @file dotnet_host.h
 * @brief .NET CoreCLR 宿主启动器
 * 
 * 此模块负责加载和初始化 CoreCLR 运行时，并启动 .NET 应用程序。
 * 使用从 Java 层通过 JNI 传递的参数进行启动。
 */
#pragma once

/**
 * @brief 通过 CoreCLR 启动 .NET 应用程序
 * 
 * @return 应用程序退出码（0 表示成功，负数表示启动失败）
 * 
 * 此函数执行以下操作：
 * 1. 从全局变量中获取启动参数（由 Java 层通过 JNI 设置）
 * 2. 设置工作目录和 LD_LIBRARY_PATH 环境变量
 * 3. 加载 libcoreclr.so 动态库
 * 4. 初始化 CoreCLR 运行时
 * 5. 执行主程序集（.dll 文件）
 * 6. 关闭 CoreCLR 并清理资源
 * 
 * 错误码：
 * - -11: 无法加载 libcoreclr.so
 * - -12: 无法找到 CoreCLR 函数符号
 * - -13: CoreCLR 初始化失败
 * - -20: 程序集执行失败
 * 
 * @note 此函数依赖于以下全局变量：
 *       - h_appPath: 主程序集路径
 *       - h_appDir: 应用程序目录
 *       - g_trustedAssemblies: 受信程序集列表
 *       - g_nativeSearchPaths: 原生库搜索路径
 *       - g_launcherDll: 启动器 DLL 路径
 */
int launch_with_coreclr_passthrough();


