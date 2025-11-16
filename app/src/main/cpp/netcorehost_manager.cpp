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
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <string>
#include <vector>
#include <memory>
#include <map>
#include "app_logger.h"

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
        LOGI(LOG_TAG, "已经初始化，跳过");
        return 0;
    }

    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "🔧 初始化 .NET Core Host Manager");
    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "  DOTNET_ROOT: %s", dotnet_root ? dotnet_root : "(自动检测)");
    LOGI(LOG_TAG, "  框架版本: %d.x", framework_major);

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
        LOGI(LOG_TAG, "  滚动策略: LatestMajor (net%d.x)", framework_major);
    } else {
        setenv("DOTNET_ROLL_FORWARD", "LatestMajor", 1);
        setenv("DOTNET_ROLL_FORWARD_ON_NO_CANDIDATE_FX", "2", 1);
        setenv("DOTNET_ROLL_FORWARD_TO_PRERELEASE", "1", 1);
        LOGI(LOG_TAG, "  滚动策略: 自动（最新版本）");
    }

    // 启用调试输出
    setenv("COREHOST_TRACE", "1", 1);
    setenv("COMPlus_DebugWriteToStdErr", "1", 1);

    // 输入相关
    setenv("SDL_TOUCH_MOUSE_EVENTS", "1", 1);

    // 初始化 JNI Bridge
    LOGI(LOG_TAG, "初始化 JNI Bridge...");
    JavaVM* jvm = Bridge_GetJavaVM();
    JNIEnv* env = nullptr;
    if (jvm) {
        env = Bridge_GetJNIEnv();
        if (env) {
            LOGI(LOG_TAG, "  JNI Bridge OK (JVM: %p, Env: %p)", jvm, env);
        } else {
            LOGW(LOG_TAG, "  无法获取 JNIEnv");
        }
    } else {
        LOGW(LOG_TAG, "  JavaVM 未初始化");
    }

    try {
        // 加载 hostfxr
        LOGI(LOG_TAG, "加载 hostfxr...");
        g_hostfxr = netcorehost::Nethost::load_hostfxr();

        if (!g_hostfxr) {
            set_error("hostfxr 加载失败");
            return -1;
        }

        LOGI(LOG_TAG, "✓ hostfxr 加载成功");
        LOGI(LOG_TAG, "========================================");
        g_initialized = true;
        g_last_error[0] = '\0';
        return 0;

    } catch (const netcorehost::HostingException& ex) {
        set_error("初始化失败（托管异常）: %s", ex.what());
        return -1;
    } catch (const std::exception& ex) {
        set_error("初始化失败: %s", ex.what());
        return -1;
    }
}

/**
 * @brief 运行程序集（调用 Main 入口点）
 */
int netcore_run_app(
    const char* app_dir,
    const char* main_assembly,
    int argc,
    const char* const* argv) {

    if (!g_initialized) {
        set_error("未初始化，请先调用 netcore_init()");
        return -1;
    }

    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "🚀 运行程序集: %s", main_assembly);
    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "  目录: %s", app_dir);
    LOGI(LOG_TAG, "  参数数量: %d", argc);

    // 构建完整程序集路径
    std::string app_path = std::string(app_dir) + "/" + std::string(main_assembly);

    // 验证文件存在
    if (access(app_path.c_str(), F_OK) != 0) {
        set_error("程序集不存在: %s", app_path.c_str());
        return -1;
    }

    // 设置工作目录
    if (chdir(app_dir) == 0) {
        LOGI(LOG_TAG, "  工作目录: %s", app_dir);
    } else {
        LOGW(LOG_TAG, "  无法设置工作目录");
    }

    // 设置环境变量
    setenv("XDG_DATA_HOME", app_dir, 1);
    setenv("XDG_CONFIG_HOME", app_dir, 1);
    setenv("HOME", app_dir, 1);

    try {
        // 初始化运行时上下文
        auto app_path_str = netcorehost::PdCString::from_str(app_path.c_str());

        std::unique_ptr<netcorehost::HostfxrContextForCommandLine> context;

        if (!g_dotnet_root.empty()) {
            auto dotnet_root_str = netcorehost::PdCString::from_str(g_dotnet_root.c_str());
            context = g_hostfxr->initialize_for_dotnet_command_line_with_dotnet_root(
                app_path_str, dotnet_root_str);
        } else {
            context = g_hostfxr->initialize_for_dotnet_command_line(app_path_str);
        }

        if (!context) {
            set_error("运行时初始化失败");
            return -1;
        }

        LOGI(LOG_TAG, "运行时初始化成功，开始执行...");
        LOGI(LOG_TAG, "========================================");

        // 运行应用
        auto result = context->run_app();
        int32_t exit_code = result.value();

        LOGI(LOG_TAG, "========================================");
        if (exit_code == 0) {
            LOGI(LOG_TAG, "✓ 程序正常退出");
            g_last_error[0] = '\0';
        } else if (exit_code < 0) {
            auto hosting_result = result.as_hosting_result();
            std::string error_msg = hosting_result.get_error_message();
            set_error("托管错误 (code: %d): %s", exit_code, error_msg.c_str());
        } else {
            LOGW(LOG_TAG, "程序退出码: %d", exit_code);
            g_last_error[0] = '\0';
        }
        LOGI(LOG_TAG, "========================================");

        return exit_code;

    } catch (const netcorehost::HostingException& ex) {
        set_error("运行失败（托管异常）: %s", ex.what());
        return -1;
    } catch (const std::exception& ex) {
        set_error("运行失败: %s", ex.what());
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
        set_error("未初始化，请先调用 netcore_init()");
        return -1;
    }

    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "📦 加载程序集: %s", assembly_name);
    LOGI(LOG_TAG, "  目录: %s", app_dir);

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
        set_error("找不到 runtimeconfig.json: %s", runtimeconfig_path.c_str());
        return -1;
    }

    // 设置工作目录
    if (chdir(app_dir) == 0) {
        LOGI(LOG_TAG, "  工作目录: %s", app_dir);
    }

    try {
        // 创建运行时上下文
        auto runtimeconfig_str = netcorehost::PdCString::from_str(runtimeconfig_path.c_str());

        std::unique_ptr<netcorehost::HostfxrContextForRuntimeConfig> runtime_ctx;

        if (!g_dotnet_root.empty()) {
            auto dotnet_root_str = netcorehost::PdCString::from_str(g_dotnet_root.c_str());
            runtime_ctx = g_hostfxr->initialize_for_runtime_config_with_dotnet_root(
                runtimeconfig_str, dotnet_root_str);
        } else {
            runtime_ctx = g_hostfxr->initialize_for_runtime_config(runtimeconfig_str);
        }

        if (!runtime_ctx) {
            set_error("运行时配置初始化失败");
            return -1;
        }

        // 获取运行时委托
        auto get_delegate_result = runtime_ctx->get_runtime_delegate(
            netcorehost::bindings::hostfxr_delegate_type::hdt_load_assembly_and_get_function_pointer);

        if (!get_delegate_result) {
            set_error("无法获取运行时委托");
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

        LOGI(LOG_TAG, "✓ 程序集加载成功 (handle: %p)", handle);
        LOGI(LOG_TAG, "========================================");
        g_last_error[0] = '\0';
        return 0;

    } catch (const netcorehost::HostingException& ex) {
        set_error("加载失败（托管异常）: %s", ex.what());
        return -1;
    } catch (const std::exception& ex) {
        set_error("加载失败: %s", ex.what());
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
        set_error("无效的上下文句柄");
        return -1;
    }

    auto& ctx = it->second;
    LOGI(LOG_TAG, "🔧 调用方法: %s::%s", type_name, method_name);

    try {
        // 获取 load_assembly_and_get_function_pointer 委托
        auto get_delegate_result = ctx->runtime_ctx->get_runtime_delegate(
            netcorehost::bindings::hostfxr_delegate_type::hdt_load_assembly_and_get_function_pointer);

        if (!get_delegate_result) {
            set_error("无法获取运行时委托");
            return -1;
        }

        auto load_assembly_and_get_function_pointer =
            get_delegate_result.value().as_load_assembly_and_get_function_pointer();

        // 构建程序集完整路径
        std::string assembly_path = ctx->app_dir + "/" + ctx->assembly_name;
        auto assembly_path_str = netcorehost::PdCString::from_str(assembly_path.c_str());
        auto type_name_str = netcorehost::PdCString::from_str(type_name);
        auto method_name_str = netcorehost::PdCString::from_str(method_name);

        void* method_ptr = nullptr;

        if (delegate_type && delegate_type[0] != '\0') {
            // 有返回值（委托）
            auto delegate_type_str = netcorehost::PdCString::from_str(delegate_type);
            auto call_result = load_assembly_and_get_function_pointer(
                assembly_path_str, type_name_str, method_name_str,
                delegate_type_str, nullptr, &method_ptr);

            if (!call_result.is_success()) {
                set_error("方法调用失败 (code: %d)", call_result.value());
                return -1;
            }

            if (result) {
                *result = method_ptr;
            }
        } else {
            // 无返回值
            auto call_result = load_assembly_and_get_function_pointer(
                assembly_path_str, type_name_str, method_name_str,
                nullptr, nullptr, &method_ptr);

            if (!call_result.is_success()) {
                set_error("方法调用失败 (code: %d)", call_result.value());
                return -1;
            }

            // 执行方法（假设是无参数的 Action）
            if (method_ptr) {
                typedef void (*action_fn)();
                ((action_fn)method_ptr)();
            }
        }

        LOGI(LOG_TAG, "✓ 方法调用成功");
        g_last_error[0] = '\0';
        return 0;

    } catch (const netcorehost::HostingException& ex) {
        set_error("调用失败（托管异常）: %s", ex.what());
        return -1;
    } catch (const std::exception& ex) {
        set_error("调用失败: %s", ex.what());
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
        LOGI(LOG_TAG, "关闭上下文: %p", context_handle);
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
    LOGI(LOG_TAG, "🧹 清理资源");
    LOGI(LOG_TAG, "  关闭 %zu 个上下文", g_contexts.size());

    g_contexts.clear();
    g_hostfxr.reset();
    g_initialized = false;
    g_last_error[0] = '\0';

    LOGI(LOG_TAG, "✓ 清理完成");
    LOGI(LOG_TAG, "========================================");
}
