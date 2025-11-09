/**
 * @file netcorehost_launcher.cpp
 * @brief 简化的 .NET 启动器实现（使用 netcorehost API）
 * 

 */

#include "netcorehost_launcher.h"
#include <netcorehost/nethost.hpp>
#include <netcorehost/hostfxr.hpp>
#include <netcorehost/context.hpp>
#include <netcorehost/error.hpp>
#include <netcorehost/bindings.hpp>

// 直接声明静态链接的 nethost 函数
extern "C" {
    int32_t get_hostfxr_path(
        char* buffer,
        size_t* buffer_size,
        const netcorehost::bindings::get_hostfxr_parameters* parameters
    );
}

#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>
#include <dlfcn.h>
#include <fstream>
#include <string>
#include <vector>
#include <memory>

#define LOG_TAG "NetCoreHost"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// 全局参数
static char* g_app_path = nullptr;      // 程序集完整路径
static char* g_dotnet_path = nullptr;   // .NET 运行时路径
static int g_framework_major = 0;        // 框架主版本号（如 8 表示 .NET 8.0.0）

/**
 * @brief 辅助函数：复制字符串
 */
static char* str_dup(const char* str) {
    if (!str) return nullptr;
    return strdup(str);
}

/**
 * @brief 辅助函数：释放字符串
 */
static void str_free(char*& str) {
    if (str) {
        free(str);
        str = nullptr;
    }
}

/**
 * @brief 从完整路径提取目录
 */
static std::string get_directory(const std::string& path) {
    size_t pos = path.find_last_of("/\\");
    if (pos != std::string::npos) {
        return path.substr(0, pos);
    }
    return ".";
}

// 注意：参考 Rust 版本，不生成 runtimeconfig.json
// hostfxr 会自动查找 {assembly}.runtimeconfig.json，如果不存在会使用默认配置

/**
 * @brief 设置启动参数（保持与旧 API 兼容）
 */
int netcorehost_set_params(
    const char* app_dir, 
    const char* main_assembly,
    const char* dotnet_root,
    int framework_major) {
    
    // 清理旧参数
    str_free(g_app_path);
    str_free(g_dotnet_path);
    
    // 构建完整程序集路径
    std::string full_path = std::string(app_dir) + "/" + main_assembly;
    g_app_path = str_dup(full_path.c_str());
    g_dotnet_path = str_dup(dotnet_root);
    g_framework_major = framework_major;
    
    if (!g_app_path) {
        LOGE("Failed to set parameters");
        return -1;
    }
    
    LOGI("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    LOGI("📦 程序集路径: %s", g_app_path);
    LOGI("🔧 运行时路径: %s", g_dotnet_path ? g_dotnet_path : "(auto)");
    LOGI("🔢 框架版本: %d.0.0", g_framework_major);
    LOGI("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    
    return 0;
}

/**
 * @brief 启动 .NET 应用程序（参考 Rust 版本，直接启动）
 */
int netcorehost_launch() {
    // 验证参数
    if (!g_app_path) {
        LOGE("Parameters not set. Call netcorehost_set_params first.");
        return -1;
    }
    
    LOGI("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    LOGI("🚀 启动 .NET 程序集");
    LOGI("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    
    try {
        // 提取应用目录
        std::string app_dir = get_directory(g_app_path);
        
        // 切换工作目录
        if (chdir(app_dir.c_str()) != 0) {
            LOGE("Failed to change directory to: %s", app_dir.c_str());
            return -1;
        }
        LOGI("✓ 工作目录: %s", app_dir.c_str());
        
        // 设置环境变量
        if (g_dotnet_path) {
            setenv("DOTNET_ROOT", g_dotnet_path, 1);
            
           
        }
        setenv("APP_CONTEXT_BASE_DIRECTORY", app_dir.c_str(), 1);
        
        // CoreCLR 优化配置
        setenv("COMPlus_gcServer", "0", 1);
        setenv("COMPlus_gcConcurrent", "0", 1);
        setenv("COMPlus_TieredCompilation", "0", 1);
        setenv("COMPlus_EnableEventLog", "0", 1);
        
        // FNA 渲染器默认配置（OpenGL ES 3）
        setenv("FNA3D_FORCE_DRIVER", "OpenGL", 1);
        setenv("FNA3D_OPENGL_FORCE_ES3", "1", 1);
        setenv("FNA3D_OPENGL_FORCE_VER_MAJOR", "3", 1);
        setenv("FNA3D_OPENGL_FORCE_VER_MINOR", "0", 1);
        setenv("FNA3D_OPENGL_FORCE_COMPATIBILITY_PROFILE", "1", 1);
        setenv("SDL_OPENGL_ES_DRIVER", "1", 1);
        
        LOGI("✓ 环境变量已配置");
        LOGI("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
    // 加载 hostfxr（完全参考 Rust 版本：先设置环境变量，然后不带参数调用）
    LOGI("⏳ 正在加载 hostfxr...");
    
    // 启用 .NET 主机详细跟踪（用于诊断依赖解析问题）
    setenv("COREHOST_TRACE", "1", 1);
    setenv("COREHOST_TRACEFILE", "/sdcard/Android/data/com.app.ralaunch/files/corehost_trace.log", 1);
    LOGI("✓ 已启用 COREHOST_TRACE，日志将写入 /sdcard/Android/data/com.app.ralaunch/files/corehost_trace.log");
    
    // 关键：Rust 版本先设置 DOTNET_ROOT 环境变量，然后 nethost 会自动读取它
    if (g_dotnet_path) {
        LOGI("⏳ 设置 DOTNET_ROOT 环境变量: %s", g_dotnet_path);
        setenv("DOTNET_ROOT", g_dotnet_path, 1);
    }
    
    std::shared_ptr<netcorehost::Hostfxr> hostfxr;
    
    try {
        // 完全模仿 Rust 版本：nethost::load_hostfxr() 不带参数
        // nethost 会自动从 DOTNET_ROOT 环境变量读取路径
        LOGI("⏳ 调用 nethost::load_hostfxr()（不带参数，自动读取环境变量）...");
        hostfxr = netcorehost::Nethost::load_hostfxr();
    } catch (const netcorehost::HostingException& ex) {
        LOGE("❌ 加载 hostfxr 失败");
        LOGE("  Error Code: 0x%08X", ex.error_code());
        LOGE("  Message: %s", ex.what());
        return -1;
    } catch (const std::exception& e) {
        LOGE("❌ 加载 hostfxr 时抛出异常: %s", e.what());
        return -1;
    } catch (...) {
        LOGE("❌ 加载 hostfxr 时抛出未知异常");
        return -1;
    }
        
        if (!hostfxr) {
            LOGE("❌ hostfxr 加载失败：返回空指针");
            return -1;
        }
        
        LOGI("✅ hostfxr 加载成功");
        
        // 初始化 .NET 运行时（参考 Rust 版本，直接调用，不生成 runtimeconfig.json）
        LOGI("⏳ 正在初始化 .NET 运行时...");
        LOGI("  程序集路径: %s", g_app_path);
        
        auto app_path_str = netcorehost::PdCString::from_str(g_app_path);
        
        std::unique_ptr<netcorehost::HostfxrContextForCommandLine> context;
        try {
            // 参考 Rust 版本：总是使用 with_dotnet_root（如果提供了 dotnet_root）
            if (g_dotnet_path) {
                auto dotnet_root_str = netcorehost::PdCString::from_str(g_dotnet_path);
                LOGI("⏳ 调用 initialize_for_dotnet_command_line_with_dotnet_root...");
                LOGI("  dotnet_root: %s", g_dotnet_path);
                context = hostfxr->initialize_for_dotnet_command_line_with_dotnet_root(
                    app_path_str, dotnet_root_str);
            } else {
                LOGI("⏳ 调用 initialize_for_dotnet_command_line...");
                context = hostfxr->initialize_for_dotnet_command_line(app_path_str);
            }
        } catch (const std::exception& e) {
            LOGE("❌ initialize_for_dotnet_command_line 抛出异常: %s", e.what());
            return -1;
        }
        
        if (!context) {
            LOGE("❌ .NET 运行时初始化失败：返回空指针");
            return -1;
        }
        
        LOGI("✅ .NET 运行时初始化成功");
        
        // 在运行应用前应用 MonoMod 补丁（如果存在）
        // 注意：补丁应用是可选的，如果失败会继续运行应用程序
        // 详细实现请参考 docs/HOSTFXR_PATCH_INJECTION.md
        
        // 运行应用程序
        LOGI("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        LOGI("▶️  启动应用程序...");
        LOGI("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        auto app_result = context->run_app();
        int32_t exit_code = app_result.value();
        
        LOGI("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        if (exit_code == 0) {
            LOGI("✅ 应用程序正常退出");
        } else if (exit_code < 0) {
            auto hosting_result = app_result.as_hosting_result();
            LOGE("❌ 托管错误 (code: %d)", exit_code);
            LOGE("  %s", hosting_result.get_error_message().c_str());
        } else {
            LOGW("⚠️  应用退出码: %d", exit_code);
        }
        
        LOGI("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        return exit_code;
        
    } catch (const netcorehost::HostingException& ex) {
        LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        LOGE("❌ 托管错误");
        LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        LOGE("  %s", ex.what());
        LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return -1;
    } catch (const std::exception& ex) {
        LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        LOGE("❌ 意外错误");
        LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        LOGE("  %s", ex.what());
        LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return -2;
    }
}

/**
 * @brief 清理资源
 */
void netcorehost_cleanup() {
    str_free(g_app_path);
    str_free(g_dotnet_path);
    LOGI("Cleanup complete");
}

// ============================================================================
// JNI 导出函数
// ============================================================================

extern "C" {

/**
 * @brief JNI: 设置启动参数
 */
JNIEXPORT jint JNICALL
Java_com_app_ralaunch_game_GameLauncher_netcorehostSetParams(
    JNIEnv *env, jclass clazz,
    jstring appDir, jstring mainAssembly, jstring dotnetRoot, jint frameworkMajor) {
    
    const char *app_dir = env->GetStringUTFChars(appDir, nullptr);
    const char *main_assembly = env->GetStringUTFChars(mainAssembly, nullptr);
    const char *dotnet_root = dotnetRoot ? env->GetStringUTFChars(dotnetRoot, nullptr) : nullptr;
    
    int result = netcorehost_set_params(app_dir, main_assembly, dotnet_root, frameworkMajor);
    
    env->ReleaseStringUTFChars(appDir, app_dir);
    env->ReleaseStringUTFChars(mainAssembly, main_assembly);
    if (dotnet_root) env->ReleaseStringUTFChars(dotnetRoot, dotnet_root);
    
    return result;
}

/**
 * @brief JNI: 启动应用程序
 */
JNIEXPORT jint JNICALL
Java_com_app_ralaunch_game_GameLauncher_netcorehostLaunch(JNIEnv *env, jclass clazz) {
    return netcorehost_launch();
}

/**
 * @brief JNI: 清理资源
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_game_GameLauncher_netcorehostCleanup(JNIEnv *env, jclass clazz) {
    netcorehost_cleanup();
}

} // extern "C"
