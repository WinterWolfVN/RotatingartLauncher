/**
 * @file netcorehost_manager.h
 * @brief .NET Core Host 管理器 - 支持多程序集和方法调用
 *
 * 功能：
 * 1. 一次性初始化 .NET 运行时环境
 * 2. 运行多个不同的程序集（独立上下文，互不干扰）
 * 3. 调用程序集的任意静态方法
 * 4. 获取和设置程序集的属性
 */
#pragma once

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @brief 初始化 .NET 运行时环境（只需调用一次）
 *
 * @param dotnet_root .NET 运行时根目录
 * @param framework_major 框架主版本号（如 8 表示 .NET 8，0 表示最高版本）
 * @return 0 成功，负数表示失败
 */
int netcore_init(const char* dotnet_root, int framework_major);

/**
 * @brief 运行指定的 .NET 程序集（调用 Main 入口点）
 *
 * @param app_dir 应用程序目录（包含 .dll 和 .runtimeconfig.json）
 * @param main_assembly 主程序集名称（如 "MyGame.dll"）
 * @param argc 命令行参数数量（0 表示无参数）
 * @param argv 命令行参数数组（NULL 表示无参数）
 * @return 应用程序退出码（0 表示成功）
 *
 * 注意：此函数会阻塞直到程序集执行完毕
 */
int netcore_run_app(
    const char* app_dir,
    const char* main_assembly,
    int argc,
    const char* const* argv);

/**
 * @brief 加载程序集并获取上下文句柄（用于后续方法调用）
 *
 * @param app_dir 应用程序目录
 * @param assembly_name 程序集名称（如 "MyLibrary.dll"）
 * @param context_handle 输出参数，接收上下文句柄
 * @return 0 成功，负数表示失败
 *
 * 使用示例：
 *   void* ctx;
 *   netcore_load_assembly("/path/to/app", "MyLib.dll", &ctx);
 *   netcore_call_method(ctx, "MyNamespace.MyClass, MyLib", "MyMethod", nullptr, nullptr);
 *   netcore_close_context(ctx);
 */
int netcore_load_assembly(
    const char* app_dir,
    const char* assembly_name,
    void** context_handle);

/**
 * @brief 调用程序集的静态方法
 *
 * @param context_handle 程序集上下文句柄（由 netcore_load_assembly 返回）
 * @param type_name 类型全名（格式："命名空间.类名, 程序集名"，如 "MyApp.Utils, MyApp"）
 * @param method_name 方法名称
 * @param delegate_type 委托类型字符串（可选，如 "MyApp.MyDelegate, MyApp"）
 * @param result 输出参数，接收方法返回的委托指针（可选，NULL 表示不需要返回值）
 * @return 0 成功，负数表示失败
 *
 * 使用示例：
 *   // 调用无返回值的方法
 *   netcore_call_method(ctx, "MyApp.Startup, MyApp", "Initialize", nullptr, nullptr);
 *
 *   // 调用有返回值的方法（返回委托）
 *   void* func_ptr;
 *   netcore_call_method(ctx, "MyApp.Math, MyApp", "GetAddFunction",
 *                       "MyApp.AddDelegate, MyApp", &func_ptr);
 */
int netcore_call_method(
    void* context_handle,
    const char* type_name,
    const char* method_name,
    const char* delegate_type,
    void** result);

/**
 * @brief 获取程序集的属性值
 *
 * @param context_handle 程序集上下文句柄
 * @param type_name 类型全名
 * @param property_name 属性名称
 * @param delegate_type 委托类型字符串
 * @param result 输出参数，接收属性值（委托指针）
 * @return 0 成功，负数表示失败
 */
int netcore_get_property(
    void* context_handle,
    const char* type_name,
    const char* property_name,
    const char* delegate_type,
    void** result);

/**
 * @brief 关闭程序集上下文并释放资源
 *
 * @param context_handle 要关闭的上下文句柄
 */
void netcore_close_context(void* context_handle);

/**
 * @brief 获取最后一次错误的详细消息
 * @return 错误消息字符串，如果没有错误则返回 NULL
 */
const char* netcore_get_last_error();

/**
 * @brief 清理所有资源（释放 hostfxr 和所有上下文）
 */
void netcore_cleanup();

#ifdef __cplusplus
}
#endif
