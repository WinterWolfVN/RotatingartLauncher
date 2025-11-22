/**
 * @file netcorehost_manager.cpp
 * @brief .NET Core Host 管理器实现
 */

#include "netcorehost_manager.h"
#include <netcorehost/nethost.hpp>
#include <netcorehost/hostfxr.hpp>
#include <netcorehost/context.hpp>
#include <netcorehost/error.hpp>
#include <netcorehost/bindings.hpp>
#include <netcorehost/delegate_loader.hpp>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <string>
#include <vector>
#include <memory>
#include <map>
#include "app_logger.h"
#include "corehost_trace_redirect.h"

// 直接声明静态链接的 nethost 函数
extern "C" {
int32_t get_hostfxr_path(
    char* buffer,
    size_t* buffer_size,
    const netcorehost::bindings::get_hostfxr_parameters* parameters);
JNIEnv* Bridge_GetJNIEnv();
JavaVM* Bridge_GetJavaVM();
}

#define LOG_TAG "NetCoreManager"

// 全局状态
static std::shared_ptr<netcorehost::Hostfxr> g_hostfxr;
static std::string g_dotnet_root;
static int g_framework_major = 0;
static bool g_initialized = false;
static char g_last_error[2048] = {0};
static bool g_enable_corehost_trace = true;  // 默认启用，用于调试

// 上下文管理（每个程序集一个独立上下文）
struct AssemblyContext {
    std::unique_ptr<netcorehost::HostfxrContextForRuntimeConfig> runtime_ctx;
    std::string app_dir;
    std::string assembly_name;
};

static std::map<void*, std::unique_ptr<AssemblyContext>> g_contexts;
static int g_next_context_id = 1;

/**
 * @brief 设置错误消息
 */
static void set_error(const char* format, ...) {
    va_list args;
    va_start(args, format);
    vsnprintf(g_last_error, sizeof(g_last_error), format, args);
    va_end(args);
    LOGE(LOG_TAG, "%s", g_last_error);
}

/**
 * @brief 初始化 .NET 运行时环境
 */
int netcore_init(const char* dotnet_root, int framework_major) {
    if (g_initialized) {
        LOGI(LOG_TAG, "Already initialized, skipping");
        return 0;
    }

    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "🔧 Initializing .NET Core Host Manager");
    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "  DOTNET_ROOT: %s", dotnet_root ? dotnet_root : "(auto-detect)");
    LOGI(LOG_TAG, "  Framework version: %d.x", framework_major);

    // 保存配置
    if (dotnet_root) {
        g_dotnet_root = dotnet_root;
        setenv("DOTNET_ROOT", dotnet_root, 1);
    }
    g_framework_major = framework_major;

    // 设置运行时策略
    if (framework_major > 0) {
        setenv("DOTNET_ROLL_FORWARD", "LatestMajor", 1);
        setenv("DOTNET_ROLL_FORWARD_ON_NO_CANDIDATE_FX", "2", 1);
        setenv("DOTNET_ROLL_FORWARD_TO_PRERELEASE", "1", 1);
        LOGI(LOG_TAG, "  Roll forward policy: LatestMajor (net%d.x)", framework_major);
    } else {
        setenv("DOTNET_ROLL_FORWARD", "LatestMajor", 1);
        setenv("DOTNET_ROLL_FORWARD_ON_NO_CANDIDATE_FX", "2", 1);
        setenv("DOTNET_ROLL_FORWARD_TO_PRERELEASE", "1", 1);
        LOGI(LOG_TAG, "  Roll forward policy: automatic (latest version)");
    }

    // 根据设置决定是否启用 COREHOST_TRACE
    if (g_enable_corehost_trace) {
        init_corehost_trace_redirect();
            LOGI(LOG_TAG, "COREHOST_TRACE redirect initialized");

            // 启用 COREHOST_TRACE 以便捕获所有 .NET runtime 的 trace 输出
            setenv("COREHOST_TRACE", "1", 1);
            LOGI(LOG_TAG, "COREHOST_TRACE enabled");
        } else {
            LOGI(LOG_TAG, "COREHOST_TRACE disabled (verbose logging off)");
    }

    // 输入相关
    setenv("SDL_TOUCH_MOUSE_EVENTS", "1", 1);

    // 初始化 JNI Bridge
    LOGI(LOG_TAG, "Initializing JNI Bridge...");
    JavaVM* jvm = Bridge_GetJavaVM();
    JNIEnv* env = nullptr;
    if (jvm) {
        env = Bridge_GetJNIEnv();
        if (env) {
            LOGI(LOG_TAG, "  JNI Bridge OK (JVM: %p, Env: %p)", jvm, env);
        } else {
            LOGW(LOG_TAG, "  Cannot get JNIEnv");
        }
    } else {
        LOGW(LOG_TAG, "  JavaVM not initialized");
    }

    try {
        // 加载 hostfxr
        LOGI(LOG_TAG, "Loading hostfxr...");
        g_hostfxr = netcorehost::Nethost::load_hostfxr();

        if (!g_hostfxr) {
            set_error("hostfxr loading failed");
            return -1;
        }

        LOGI(LOG_TAG, "✓ hostfxr loaded successfully");
        LOGI(LOG_TAG, "========================================");
        g_initialized = true;
        g_last_error[0] = '\0';
        return 0;

    } catch (const netcorehost::HostingException& ex) {
        set_error("Initialization failed (hosting exception): %s", ex.what());
        return -1;
    } catch (const std::exception& ex) {
        set_error("Initialization failed: %s", ex.what());
        return -1;
    }
}

/**
 * @brief 运行程序集（调用 Main 入口点）
 *
 * 注意：此方法使用 initialize_for_dotnet_command_line，不支持传递命令行参数
 * 如果需要传递参数，请使用 netcore_run_app_with_args()
 */
int netcore_run_app(
    const char* app_dir,
    const char* main_assembly,
    int argc,
    const char* const* argv) {

    if (!g_initialized) {
        set_error("Not initialized, please call netcore_init() first");
        return -1;
    }

    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "🚀 Running assembly: %s", main_assembly);
    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "  Directory: %s", app_dir);
    LOGI(LOG_TAG, "  Argument count: %d", argc);
    for (int i = 0; i < argc; i++) {
        LOGI(LOG_TAG, "    args[%d] = %s", i, argv[i]);
    }

    // 构建完整程序集路径
    std::string app_path = std::string(app_dir) + "/" + std::string(main_assembly);

    // 验证文件存在
    if (access(app_path.c_str(), F_OK) != 0) {
        set_error("Assembly does not exist: %s", app_path.c_str());
        return -1;
    }

    // 设置工作目录
    if (chdir(app_dir) == 0) {
        LOGI(LOG_TAG, "  Working directory: %s", app_dir);
    } else {
        LOGW(LOG_TAG, "  Cannot set working directory");
    }

    // 设置环境变量
    setenv("XDG_DATA_HOME", app_dir, 1);
    setenv("XDG_CONFIG_HOME", app_dir, 1);
    setenv("HOME", app_dir, 1);

    try {
        // 初始化运行时上下文（支持参数传递）
        auto app_path_str = netcorehost::PdCString::from_str(app_path.c_str());

        std::unique_ptr<netcorehost::HostfxrContextForCommandLine> context;

        // 根据是否有参数选择合适的初始化方法
        if (argc > 0 && argv != nullptr) {
            // 有参数：使用带参数的初始化方法
            if (!g_dotnet_root.empty()) {
                auto dotnet_root_str = netcorehost::PdCString::from_str(g_dotnet_root.c_str());
                context = g_hostfxr->initialize_for_dotnet_command_line_with_args_and_dotnet_root(
                    app_path_str, argc, argv, dotnet_root_str);
            } else {
                context = g_hostfxr->initialize_for_dotnet_command_line_with_args(
                    app_path_str, argc, argv);
            }
        } else {
            // 无参数：使用原始方法
            if (!g_dotnet_root.empty()) {
                auto dotnet_root_str = netcorehost::PdCString::from_str(g_dotnet_root.c_str());
                context = g_hostfxr->initialize_for_dotnet_command_line_with_dotnet_root(
                    app_path_str, dotnet_root_str);
            } else {
                context = g_hostfxr->initialize_for_dotnet_command_line(app_path_str);
            }
        }

        if (!context) {
            set_error("Runtime initialization failed");
            return -1;
        }

        LOGI(LOG_TAG, "Runtime initialized successfully, starting execution...");
        LOGI(LOG_TAG, "========================================");

        // 运行应用
        auto result = context->run_app();
        int32_t exit_code = result.value();

        LOGI(LOG_TAG, "========================================");
        if (exit_code == 0) {
            LOGI(LOG_TAG, "✓ Application exited normally");
            g_last_error[0] = '\0';
        } else if (exit_code < 0) {
            auto hosting_result = result.as_hosting_result();
            std::string error_msg = hosting_result.get_error_message();
            set_error("Hosting error (code: %d): %s", exit_code, error_msg.c_str());
        } else {
            LOGW(LOG_TAG, "Application exit code: %d", exit_code);
            g_last_error[0] = '\0';
        }
        LOGI(LOG_TAG, "========================================");

        // ⚠️ 重要：先显式关闭并销毁 context，然后重置 hostfxr
        // 必须按此顺序：
        // 1. context->close() 需要调用 hostfxr 的函数，所以必须在 hostfxr 重置之前完成
        // 2. 销毁 context 后，才能安全地重置 hostfxr 实例
        LOGI(LOG_TAG, "Closing context...");
        try {
            context->close();  // 显式关闭上下文
        } catch (const std::exception& ex) {
            LOGW(LOG_TAG, "Error while closing context: %s", ex.what());
        }
        context.reset();  // 销毁 context unique_ptr
        LOGI(LOG_TAG, "✓ Context closed");

        // 现在可以安全地重置 hostfxr 以允许下一次运行
        // initialize_for_dotnet_command_line 不支持在同一个 hostfxr 实例中连续创建多个上下文
        LOGI(LOG_TAG, "Resetting hostfxr to allow next run...");
        g_hostfxr.reset();
        g_hostfxr = netcorehost::Nethost::load_hostfxr();
        if (!g_hostfxr) {
            LOGW(LOG_TAG, "⚠️ hostfxr reload failed");
        } else {
            LOGI(LOG_TAG, "✓ hostfxr reloaded successfully");
        }

        return exit_code;

    } catch (const netcorehost::HostingException& ex) {
        set_error("Run failed (hosting exception): %s", ex.what());
        return -1;
    } catch (const std::exception& ex) {
        set_error("Run failed: %s", ex.what());
        return -1;
    }
}

/**
 * @brief 加载程序集并获取上下文
 */
int netcore_load_assembly(
    const char* app_dir,
    const char* assembly_name,
    void** context_handle) {

    if (!g_initialized) {
        set_error("Not initialized, please call netcore_init() first");
        return -1;
    }

    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "📦 Loading assembly: %s", assembly_name);
    LOGI(LOG_TAG, "  Directory: %s", app_dir);

    // 构建 runtimeconfig.json 路径
    std::string assembly_name_str(assembly_name);
    std::string base_name = assembly_name_str;
    size_t dot_pos = base_name.rfind('.');
    if (dot_pos != std::string::npos) {
        base_name = base_name.substr(0, dot_pos);
    }

    std::string runtimeconfig_path = std::string(app_dir) + "/" + base_name + ".runtimeconfig.json";

    // 验证 runtimeconfig.json 存在
    if (access(runtimeconfig_path.c_str(), F_OK) != 0) {
        set_error("Cannot find runtimeconfig.json: %s", runtimeconfig_path.c_str());
        return -1;
    }

    // 设置工作目录
    if (chdir(app_dir) == 0) {
        LOGI(LOG_TAG, "  Working directory: %s", app_dir);
    }

    try {
        // 创建运行时上下文
        auto runtimeconfig_str = netcorehost::PdCString::from_str(runtimeconfig_path.c_str());

        std::unique_ptr<netcorehost::HostfxrContextForRuntimeConfig> runtime_ctx;

        // 使用 initialize_for_runtime_config 配合 parameters
        // 注意：runtime config 方法使用 hostfxr_initialize_parameters
        runtime_ctx = g_hostfxr->initialize_for_runtime_config(runtimeconfig_str);

        if (!runtime_ctx) {
            set_error("Runtime config initialization failed");
            return -1;
        }

        // 获取运行时委托
        auto get_delegate_result = runtime_ctx->get_runtime_delegate(
            netcorehost::bindings::hostfxr_delegate_type::hdt_load_assembly_and_get_function_pointer);

        if (!get_delegate_result) {
            set_error("Cannot get runtime delegate");
            return -1;
        }

        // 创建上下文对象
        auto ctx = std::make_unique<AssemblyContext>();
        ctx->runtime_ctx = std::move(runtime_ctx);
        ctx->app_dir = app_dir;
        ctx->assembly_name = assembly_name;

        // 生成句柄
        void* handle = (void*)(intptr_t)g_next_context_id++;
        g_contexts[handle] = std::move(ctx);
        *context_handle = handle;

        LOGI(LOG_TAG, "✓ Assembly loaded successfully (handle: %p)", handle);
        LOGI(LOG_TAG, "========================================");
        g_last_error[0] = '\0';
        return 0;

    } catch (const netcorehost::HostingException& ex) {
        set_error("Load failed (hosting exception): %s", ex.what());
        return -1;
    } catch (const std::exception& ex) {
        set_error("Load failed: %s", ex.what());
        return -1;
    }
}

/**
 * @brief 调用程序集的静态方法
 */
int netcore_call_method(
    void* context_handle,
    const char* type_name,
    const char* method_name,
    const char* delegate_type,
    void** result) {

    auto it = g_contexts.find(context_handle);
    if (it == g_contexts.end()) {
        set_error("Invalid context handle");
        return -1;
    }

    auto& ctx = it->second;
    LOGI(LOG_TAG, "🔧 Calling method: %s::%s", type_name, method_name);

    try {
        // 获取 load_assembly_and_get_function_pointer 委托
        auto get_delegate_result = ctx->runtime_ctx->get_runtime_delegate(
            netcorehost::bindings::hostfxr_delegate_type::hdt_load_assembly_and_get_function_pointer);

        if (!get_delegate_result) {
            set_error("Cannot get runtime delegate");
            return -1;
        }

        auto load_assembly_and_get_function_pointer =
            reinterpret_cast<netcorehost::bindings::load_assembly_and_get_function_pointer_fn>(get_delegate_result);

        // 构建程序集完整路径
        std::string assembly_path = ctx->app_dir + "/" + ctx->assembly_name;
        auto assembly_path_str = netcorehost::PdCString::from_str(assembly_path.c_str());
        auto type_name_str = netcorehost::PdCString::from_str(type_name);
        auto method_name_str = netcorehost::PdCString::from_str(method_name);

        void* method_ptr = nullptr;

        if (delegate_type && delegate_type[0] != '\0') {
            // 有返回值（委托）
            auto delegate_type_str = netcorehost::PdCString::from_str(delegate_type);
            int32_t call_result = load_assembly_and_get_function_pointer(
                assembly_path_str.c_str(), type_name_str.c_str(), method_name_str.c_str(),
                delegate_type_str.c_str(), nullptr, &method_ptr);

            if (call_result != 0) {
                set_error("Method call failed (code: %d)", call_result);
                return -1;
            }

            if (result) {
                *result = method_ptr;
            }
        } else {
            // 无返回值
            int32_t call_result = load_assembly_and_get_function_pointer(
                assembly_path_str.c_str(), type_name_str.c_str(), method_name_str.c_str(),
                nullptr, nullptr, &method_ptr);

            if (call_result != 0) {
                set_error("Method call failed (code: %d)", call_result);
                return -1;
            }

            // 执行方法（假设是无参数的 Action）
            if (method_ptr) {
                typedef void (*action_fn)();
                ((action_fn)method_ptr)();
            }
        }

        LOGI(LOG_TAG, "✓ Method called successfully");
        g_last_error[0] = '\0';
        return 0;

    } catch (const netcorehost::HostingException& ex) {
        set_error("Call failed (hosting exception): %s", ex.what());
        return -1;
    } catch (const std::exception& ex) {
        set_error("Call failed: %s", ex.what());
        return -1;
    }
}

/**
 * @brief 获取程序集的属性值
 */
int netcore_get_property(
    void* context_handle,
    const char* type_name,
    const char* property_name,
    const char* delegate_type,
    void** result) {

    // 属性通过 get_PropertyName 方法访问
    std::string getter_name = std::string("get_") + property_name;
    return netcore_call_method(context_handle, type_name, getter_name.c_str(),
                               delegate_type, result);
}

/**
 * @brief 关闭程序集上下文
 */
void netcore_close_context(void* context_handle) {
    auto it = g_contexts.find(context_handle);
    if (it != g_contexts.end()) {
        LOGI(LOG_TAG, "Closing context: %p", context_handle);
        g_contexts.erase(it);
    }
}

/**
 * @brief 获取最后一次错误
 */
const char* netcore_get_last_error() {
    if (g_last_error[0] == '\0') {
        return nullptr;
    }
    return g_last_error;
}

/**
 * @brief 清理所有资源
 */
void netcore_cleanup() {
    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "🧹 Cleaning up resources");
    LOGI(LOG_TAG, "  Closing %zu context(s)", g_contexts.size());

    g_contexts.clear();
    g_hostfxr.reset();
    g_initialized = false;
    g_last_error[0] = '\0';

    LOGI(LOG_TAG, "✓ Cleanup complete");
    LOGI(LOG_TAG, "========================================");
}

/**
 * @brief 运行工具程序集（使用 runtime config，支持在已加载的 CoreCLR 中运行）
 *
 * 此函数专门用于运行工具程序（如 AssemblyChecker、InstallerTools），
 * 与 netcore_run_app() 的区别：
 * - netcore_run_app() 使用 initialize_for_dotnet_command_line，会加载 CoreCLR（primary context）
 * - netcore_run_tool() 使用 initialize_for_runtime_config，可以在已加载的 CoreCLR 中运行（secondary context）
 *
 * 重要：如果 CoreCLR 已被 netcore_run_app() 加载，则后续只能使用此函数，不能再用 netcore_run_app()
 *
 * @param app_dir 工具程序所在目录
 * @param tool_assembly 工具程序集名称（如 "AssemblyChecker.dll"）
 * @param argc 命令行参数数量
 * @param argv 命令行参数数组
 * @return 工具程序退出码（Main方法的返回值）
 */
int netcore_run_tool(
    const char* app_dir,
    const char* tool_assembly,
    int argc,
    const char* const* argv) {

    if (!g_initialized) {
        set_error("Not initialized, please call netcore_init() first");
        return -1;
    }

    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "🔧 Running tool: %s", tool_assembly);
    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "  Directory: %s", app_dir);
    LOGI(LOG_TAG, "  Argument count: %d", argc);
    for (int i = 0; i < argc; i++) {
        LOGI(LOG_TAG, "    args[%d] = %s", i, argv[i]);
    }

    // 构建 runtimeconfig.json 路径
    std::string assembly_name_str(tool_assembly);
    std::string base_name = assembly_name_str;
    size_t dot_pos = base_name.rfind('.');
    if (dot_pos != std::string::npos) {
        base_name = base_name.substr(0, dot_pos);
    }

    std::string runtimeconfig_path = std::string(app_dir) + "/" + base_name + ".runtimeconfig.json";
    std::string assembly_path = std::string(app_dir) + "/" + tool_assembly;

    // 验证文件存在
    if (access(runtimeconfig_path.c_str(), F_OK) != 0) {
        set_error("Cannot find runtimeconfig.json: %s", runtimeconfig_path.c_str());
        return -1;
    }
    if (access(assembly_path.c_str(), F_OK) != 0) {
        set_error("Tool assembly does not exist: %s", assembly_path.c_str());
        return -1;
    }

    // 设置工作目录
    if (chdir(app_dir) == 0) {
        LOGI(LOG_TAG, "  Working directory: %s", app_dir);
    } else {
        LOGW(LOG_TAG, "  Cannot set working directory");
    }

    try {
        // 使用 initialize_for_runtime_config 创建上下文
        // 这允许在已加载的 CoreCLR 中运行（secondary context）
        auto runtimeconfig_str = netcorehost::PdCString::from_str(runtimeconfig_path.c_str());

        // C++ netcorehost 库暂时不支持 with_dotnet_root，只能使用基础版本
        // dotnet_root 已通过环境变量 DOTNET_ROOT 设置，hostfxr 会自动读取
        auto context = g_hostfxr->initialize_for_runtime_config(runtimeconfig_str);

        if (!context) {
            set_error("Runtime config initialization failed");
            return -1;
        }

        LOGI(LOG_TAG, "Runtime config loaded successfully (is_primary: %s)",
             context->is_primary() ? "true" : "false");

        // 获取委托加载器（不绑定特定程序集，使用默认 AssemblyLoadContext）
        auto delegate_loader = context->get_delegate_loader();

        if (!delegate_loader) {
            set_error("Cannot get delegate loader");
            return -1;
        }

        // 查找并调用 ComponentEntryPoint 方法
        // 这是一个包装方法，使用 ComponentEntryPoint 签名，内部调用 Main
        auto assembly_path_str = netcorehost::PdCString::from_str(assembly_path.c_str());
        auto type_and_assembly = netcorehost::PdCString::from_str(
            (base_name + ".Program, " + base_name).c_str());
        auto method_name = netcorehost::PdCString::from_str("ComponentEntryPoint");

        // 将参数序列化为 JSON 并设置到环境变量
        // C# 代码会从 DOTNET_TOOL_ARGS 环境变量读取参数
        if (argc > 0 && argv != nullptr) {
            std::string args_json = "[";
            for (int i = 0; i < argc; i++) {
                if (i > 0) args_json += ",";
                // 简单的 JSON 转义（足够用于路径）
                std::string arg_escaped = argv[i];
                // 替换反斜杠和引号
                size_t pos = 0;
                while ((pos = arg_escaped.find('\\', pos)) != std::string::npos) {
                    arg_escaped.replace(pos, 1, "\\\\");
                    pos += 2;
                }
                pos = 0;
                while ((pos = arg_escaped.find('"', pos)) != std::string::npos) {
                    arg_escaped.replace(pos, 1, "\\\"");
                    pos += 2;
                }
                args_json += "\"" + arg_escaped + "\"";
            }
            args_json += "]";
            setenv("DOTNET_TOOL_ARGS", args_json.c_str(), 1);
            LOGI(LOG_TAG, "Set argument environment variable: %s", args_json.c_str());
        } else {
            setenv("DOTNET_TOOL_ARGS", "[]", 1);
        }

        // 使用默认委托签名：int ComponentEntryPoint(IntPtr args, int sizeBytes)
        // C++ netcorehost 的 get_function_with_default_signature 返回固定类型
        // 需要显式加载程序集
        netcorehost::bindings::component_entry_point_fn entry_fn = nullptr;
        try {
            entry_fn = delegate_loader->get_function_with_default_signature(
                assembly_path_str, type_and_assembly, method_name);
        } catch (const std::exception& ex) {
            set_error("Cannot find ComponentEntryPoint method: %s", ex.what());
            return -1;
        }

        if (!entry_fn) {
            set_error("ComponentEntryPoint method delegate is null");
            return -1;
        }

        LOGI(LOG_TAG, "Found ComponentEntryPoint method, starting execution...");
        LOGI(LOG_TAG, "========================================");

        // 调用 ComponentEntryPoint，它会从环境变量读取参数并调用 Main
        int32_t exit_code = entry_fn(nullptr, 0);

        // 清理环境变量
        unsetenv("DOTNET_TOOL_ARGS");

        LOGI(LOG_TAG, "========================================");
        if (exit_code == 0) {
            LOGI(LOG_TAG, "✓ Tool exited normally");
            g_last_error[0] = '\0';
        } else {
            LOGW(LOG_TAG, "Tool exit code: %d", exit_code);
            g_last_error[0] = '\0';
        }
        LOGI(LOG_TAG, "========================================");

        // 显式关闭上下文
        LOGI(LOG_TAG, "Closing tool context...");
        try {
            context->close();
        } catch (const std::exception& ex) {
            LOGW(LOG_TAG, "Error while closing context: %s", ex.what());
        }
        context.reset();
        LOGI(LOG_TAG, "✓ Context closed");

        return exit_code;

    } catch (const netcorehost::HostingException& ex) {
        set_error("Run failed (hosting exception): %s", ex.what());
        return -1;
    } catch (const std::exception& ex) {
        set_error("Run failed: %s", ex.what());
        return -1;
    }
}
