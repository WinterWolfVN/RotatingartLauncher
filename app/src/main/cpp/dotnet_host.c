/**
 * @file dotnet_host.c
 * @brief .NET CoreCLR 宿主启动器实现
 */

#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <jni.h>
#include <android/log.h>
#include <signal.h>
#include <ucontext.h>
#include "dotnet_params.h"
#include "jni_bridge.h"

#define LOG_TAG "GameLauncher"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

/** 主程序集路径 */
char* h_appPath = NULL;

/** .NET 运行时路径（可选） */
char* h_dotnetPath = NULL;

/** 应用程序目录 */
char* h_appDir = NULL;

/** 受信程序集列表 */
char* g_trustedAssemblies = NULL;

/** 原生库搜索路径 */
char* g_nativeSearchPaths = NULL;

/** 启动器 DLL 路径 */
char* g_launcherDll = NULL;

/**
 * @brief 信号处理函数：捕获崩溃信号并记录详细信息
 * 
 * @param sig 信号编号
 * @param si 信号信息结构
 * @param context 上下文信息（包含寄存器状态等）
 * 
 * 此函数会在程序收到致命信号（SIGSEGV, SIGABRT, SIGBUS, SIGFPE, SIGILL）时被调用，
 * 记录崩溃时的详细信息，包括信号类型、故障地址、寄存器状态等。
 */
static void signal_handler(int sig, siginfo_t *si, void *context) {
    LOGE("=====================================================");
    LOGE("🔴 FATAL SIGNAL CAUGHT: %d", sig);
    LOGE("=====================================================");
    
    // 记录信号类型
    const char* sig_name = "UNKNOWN";
    switch(sig) {
        case SIGSEGV: sig_name = "SIGSEGV (Segmentation Fault)"; break;
        case SIGABRT: sig_name = "SIGABRT (Abort)"; break;
        case SIGBUS: sig_name = "SIGBUS (Bus Error)"; break;
        case SIGFPE: sig_name = "SIGFPE (Floating Point Exception)"; break;
        case SIGILL: sig_name = "SIGILL (Illegal Instruction)"; break;
    }
    LOGE("Signal: %s", sig_name);
    
    // 记录故障地址
    if (si) {
        LOGE("Fault address: %p", si->si_addr);
        LOGE("Signal code: %d", si->si_code);
        
        // SIGSEGV 详细代码
        if (sig == SIGSEGV) {
            const char* segv_reason = "UNKNOWN";
            switch(si->si_code) {
                case SEGV_MAPERR: segv_reason = "Address not mapped"; break;
                case SEGV_ACCERR: segv_reason = "Invalid permissions"; break;
            }
            LOGE("SIGSEGV Reason: %s", segv_reason);
        }
    }
    
    // Android NDK 不支持 backtrace，跳过堆栈回溯
    // 详细的堆栈信息会在 tombstone 文件中生成
    LOGE("Stack trace will be available in tombstone file");
    
    // 记录寄存器状态（ARM64）
    if (context) {
        ucontext_t* uc = (ucontext_t*)context;
        LOGE("----- Register State (ARM64) -----");
        #ifdef __aarch64__
        mcontext_t* mc = &uc->uc_mcontext;
        LOGE("PC (程序计数器): %p", (void*)mc->pc);
        LOGE("SP (栈指针): %p", (void*)mc->sp);
        LOGE("X0: %016llx  X1: %016llx", mc->regs[0], mc->regs[1]);
        LOGE("X2: %016llx  X3: %016llx", mc->regs[2], mc->regs[3]);
        LOGE("X4: %016llx  X5: %016llx", mc->regs[4], mc->regs[5]);
        #endif
    }
    
    // 记录当前状态
    LOGE("----- Launch Parameters -----");
    LOGE("appPath: %s", h_appPath ? h_appPath : "(null)");
    LOGE("appDir: %s", h_appDir ? h_appDir : "(null)");
    LOGE("dotnetPath: %s", h_dotnetPath ? h_dotnetPath : "(null)");
    LOGE("launcherDll: %s", g_launcherDll ? g_launcherDll : "(null)");
    LOGE("nativeSearchPaths: %s", g_nativeSearchPaths ? g_nativeSearchPaths : "(null)");
    
    LOGE("=====================================================");
    LOGE("🔴 CRASH INFORMATION END - Calling default handler");
    LOGE("=====================================================");
    
    // 恢复默认处理并重新触发信号
    signal(sig, SIG_DFL);
    raise(sig);
}

/**
 * @brief 安装信号处理器
 * 
 * 为多个致命信号安装处理器，以便在崩溃时捕获并记录详细信息。
 */
static void install_signal_handlers() {
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = signal_handler;
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
    sigemptyset(&sa.sa_mask);
    
    // 安装信号处理器
    sigaction(SIGSEGV, &sa, NULL);  // 段错误
    sigaction(SIGABRT, &sa, NULL);  // 中止
    sigaction(SIGBUS, &sa, NULL);   // 总线错误
    sigaction(SIGFPE, &sa, NULL);   // 浮点异常
    sigaction(SIGILL, &sa, NULL);   // 非法指令
    
    LOGI("✓ Signal handlers installed for crash detection");
}

/**
 * @brief JNI 函数：设置完整的启动参数
 * 
 * @param env JNI 环境指针
 * @param clazz Java 类引用
 * @param appPath 应用程序主程序集路径
 * @param dotnetPath .NET 运行时路径
 * @param appDir 应用程序目录
 * @param trustedAssemblies 受信程序集列表（: 分隔）
 * @param nativeSearchPaths 原生库搜索路径（: 分隔）
 * @param mainAssemblyPath 主程序集路径（启动器 DLL）
 * 
 * 此函数从 Java 层接收所有启动参数，并存储到全局变量中。
 * 旧的全局变量值会被自动释放。
 */
JNIEXPORT void JNICALL Java_com_app_ralaunch_game_GameLauncher_setLaunchParamsFull(
    JNIEnv* env, jclass clazz,
    jstring appPath, jstring dotnetPath, jstring appDir, jstring trustedAssemblies, jstring nativeSearchPaths, jstring mainAssemblyPath) {
    // 宏：释放旧值并赋予新值
    #define FREE_AND_ASSIGN(VAR, JS) do { if (VAR) free(VAR); if (JS) { const char* tmp = (*env)->GetStringUTFChars(env, JS, 0); VAR = strdup(tmp); (*env)->ReleaseStringUTFChars(env, JS, tmp); } else VAR = NULL; } while(0)
    FREE_AND_ASSIGN(h_appPath, appPath);
    FREE_AND_ASSIGN(h_dotnetPath, dotnetPath);
    FREE_AND_ASSIGN(h_appDir, appDir);
    FREE_AND_ASSIGN(g_trustedAssemblies, trustedAssemblies);
    FREE_AND_ASSIGN(g_nativeSearchPaths, nativeSearchPaths);
    FREE_AND_ASSIGN(g_launcherDll, mainAssemblyPath);
}

/**
 * @brief JNI 函数：兼容性包装（用于 NativeBridge 类）
 * 
 * 此函数提供了对新 Java 类名 NativeBridge 的兼容支持，
 * 内部直接调用 GameLauncher 的实现。
 */
JNIEXPORT void JNICALL Java_com_app_ralaunch_game_NativeBridge_setLaunchParamsFull(
    JNIEnv* env, jclass clazz,
    jstring appPath, jstring dotnetPath, jstring appDir, jstring trustedAssemblies, jstring nativeSearchPaths, jstring mainAssemblyPath) {
    Java_com_app_ralaunch_game_GameLauncher_setLaunchParamsFull(env, clazz, appPath, dotnetPath, appDir, trustedAssemblies, nativeSearchPaths, mainAssemblyPath);
}

/**
 * @brief 通过 CoreCLR 启动 .NET 应用程序
 * 
 * @return 应用程序退出码或错误码
 * 
 * 此函数是 .NET 应用程序启动的核心实现，执行以下步骤：
 * 1. 切换工作目录到应用程序目录
 * 2. 设置 LD_LIBRARY_PATH 环境变量
 * 3. 加载 CoreCLR 动态库（libcoreclr.so）
 * 4. 获取 CoreCLR API 函数指针
 * 5. 初始化 CoreCLR 运行时
 * 6. 执行主程序集
 * 7. 关闭运行时并清理资源
 */
int launch_with_coreclr_passthrough() {
    LOGI("launch_with_coreclr_passthrough: app=%s dir=%s launcher=%s", h_appPath, h_appDir, g_launcherDll);
    
    // 0. 安装信号处理器以捕获崩溃
    install_signal_handlers();
    
    // 1. 切换到应用程序目录
    chdir(h_appDir);
    
    // 2. 设置原生库搜索路径环境变量
    if (g_nativeSearchPaths && *g_nativeSearchPaths)
        setenv("LD_LIBRARY_PATH", g_nativeSearchPaths, 1);
    
    // 2.5 设置 CoreCLR 稳定性和调试环境变量
    // 尝试使用更保守的 GC 设置来避免多线程问题
    setenv("COMPlus_gcServer", "0", 1);              // 使用工作站 GC 而不是服务器 GC
    setenv("COMPlus_gcConcurrent", "0", 1);          // 禁用并发 GC
    setenv("COMPlus_ZapDisable", "1", 1);            // 禁用 ReadyToRun
    setenv("COMPlus_ReadyToRun", "0", 1);            // 禁用 ReadyToRun（备用）
    setenv("COMPlus_EnableEventLog", "0", 1);        // 禁用事件日志
    setenv("COMPlus_DefaultStackSize", "1000000", 1); // 增加默认栈大小（1MB）
    setenv("DOTNET_LegacyThreadingModel", "1", 1);   // 使用旧线程模型
    setenv("DOTNET_EnableWriteXorExecute", "0", 1);  // 禁用 W^X 以提高稳定性
    setenv("DOTNET_GCHeapCount", "1", 1);            // 强制单个 GC 堆
    setenv("DOTNET_GCGen0Size", "10000000", 1);      // 设置 Gen0 大小为 10MB
    setenv("DOTNET_DbgEnableMiniDump", "0", 1);      // 禁用 mini dumps
    setenv("COMPlus_Thread_UseAllCpuGroups", "0", 1); // 禁用多 CPU 组
    setenv("COMPlus_GCRetainVM", "1", 1);            // GC 保留 VM（避免重复初始化）
    setenv("COMPlus_JitMinOpts", "1", 1);            // 使用最小优化（提高稳定性）
    LOGI("CoreCLR GC and optimization settings configured for stability");
    
    // 设置详细日志环境变量（如果启用）
    if (g_verboseLogging) {
        // CoreCLR 详细日志环境变量
        setenv("COREHOST_TRACE", "1", 1);
        setenv("COREHOST_TRACEFILE", "/data/local/tmp/corehost_trace.log", 1);
        setenv("COMPlus_LogEnable", "1", 1);
        setenv("COMPlus_LogLevel", "10", 1);
        setenv("COMPlus_LogToConsole", "1", 1);
        setenv("COMPlus_LogFacility", "0", 1);    // 输出到 stderr
        setenv("COMPlus_StressLog", "1", 1);
        setenv("COMPlus_StressLogSize", "65536", 1);
        
        // Mono 详细日志环境变量（兼容性）
        setenv("MONO_LOG_LEVEL", "debug", 1);
        setenv("MONO_LOG_MASK", "all", 1);
        setenv("MONO_VERBOSE_METHOD", "1", 1);
        setenv("MONO_DEBUG", "1", 1);
        setenv("MONO_TRACE_ASSEMBLY", "1", 1);
        setenv("MONO_TRACE", "all", 1);
        
        LOGI("✓ Verbose logging ENABLED - CoreCLR/Mono will output detailed diagnostic info");
    } else {
        LOGI("Verbose logging disabled (use Settings to enable for debugging)");
    }
    
    // 设置 FNA 渲染器环境变量
    if (g_renderer) {
        if (strcmp(g_renderer, "opengles3") == 0) {
            // 使用原生 OpenGL ES 3（Android 原生支持，推荐）
            setenv("FNA3D_FORCE_DRIVER", "OpenGL", 1);
            setenv("FNA3D_OPENGL_FORCE_CORE_PROFILE", "0", 1);     // 禁用 Core Profile
            setenv("FNA3D_OPENGL_FORCE_ES3", "1", 1);              // 强制使用 ES3
            setenv("FNA3D_OPENGL_FORCE_VER_MAJOR", "3", 1);        // 限制 OpenGL 主版本为 3
            setenv("FNA3D_OPENGL_FORCE_VER_MINOR", "0", 1);        // 限制 OpenGL 次版本为 0
            setenv("FNA3D_OPENGL_FORCE_COMPATIBILITY_PROFILE", "1", 1);  // 强制兼容性模式
            
            // ⚠️ 关键：告诉 SDL 使用原生 GLES 渲染器（不是 gl4es）
            setenv("FNA3D_OPENGL_DRIVER", "native", 1);
            
            // SDL hints - 忽略 GL 扩展加载错误并禁用高级特性
            setenv("SDL_VIDEO_X11_FORCE_EGL", "1", 1);
            setenv("SDL_OPENGL_ES_DRIVER", "1", 1);
            setenv("SDL_VIDEO_GL_DRIVER", "", 1);
            
            // 禁用所有不支持的OpenGL扩展和高级特性
            setenv("FNA3D_DISABLE_ARB_DEBUG_OUTPUT", "1", 1);
            setenv("FNA3D_DISABLE_ARB_EXTENSION", "1", 1);
            setenv("FNA3D_FORCE_GL_ENABLE_DEBUG_OUTPUT", "0", 1);
            
            // 禁用着色器特化（Shader Specialization）- 这是导致glSpecializeShaderARB错误的原因
            setenv("FNA3D_DISABLE_SHADER_SPECIALIZATION", "1", 1);
            
            // 强制SDL忽略扩展加载失败
            setenv("SDL_HINT_VIDEO_ALLOW_SCREENSAVER", "1", 1);
            
            LOGI("✓ FNA renderer: Native OpenGL ES 3 (best performance)");
        } else if (strcmp(g_renderer, "opengl_gl4es") == 0) {
            // 使用 gl4es 作为 OpenGL 翻译层（Android AGL 接口方案）
            // 
            // ⚠️ 架构说明（基于 gl4es AGL 接口）：
            // 
            // Android 实现原理：
            // 1. gl4es 静态链接，提供 AGL 接口（不是 EGL）
            // 2. SDL 使用自定义的 OpenGL 后端（不是 EGL）
            // 3. AGL 接口函数：
            //    - aglCreateContext2：创建 OpenGL context
            //    - aglMakeCurrent：设置当前 context
            //    - aglSwapBuffers：交换缓冲区
            //    - aglGetProcAddress：获取 OpenGL 函数指针
            //    - aglDestroyContext：销毁 context
            // 4. gl4es 在 AGL 层内部管理 EGL/GLES
            // 
            // SDL 适配（app/src/main/cpp/SDL/src/video/android/SDL_androidgl4es.c）：
            // 1. SDL 编译时定义 SDL_VIDEO_OPENGL_GL4ES
            // 2. SDL 使用 Android_GL4ES_* 函数而不是标准 EGL 函数
            // 3. gl4es 的 AGL 接口在底层管理 EGL 和 GLES
            // 4. SDL 认为自己在使用 OpenGL（兼容性 profile）
            // 
            LOGI("🔧 Configuring OpenGL via gl4es AGL interface for Android...");
            
            // ⚠️ 关键：告诉 SDL 使用 gl4es 渲染器
            setenv("FNA3D_OPENGL_DRIVER", "gl4es", 1);
            
            // ⚠️ 关键：告诉 FNA3D 使用 gl4es（用于OpenGL兼容性profile）
            // FNA3D 会使用 OpenGL Compatibility Profile
            setenv("FNA3D_USE_GL4ES", "1", 1);
            
            // ⚠️ 关键：强制使用 OpenGL driver（不是 ES）
            setenv("FNA3D_FORCE_DRIVER", "OpenGL", 1);
            
            // SDL 已在编译时配置为使用 gl4es AGL 接口（SDL_VIDEO_OPENGL_GL4ES）
            // 无需设置 SDL_VIDEO_GL_DRIVER
            
            // gl4es 环境变量配置
            // LIBGL_ES: 目标 OpenGL ES 版本（2=GLES2, 3=GLES3）
            // LIBGL_GL: 模拟的桌面 OpenGL 版本（21=2.1, 30=3.0, etc）
            setenv("LIBGL_ES", "2", 1);      // 目标 GLES 2.0（兼容性最好）
            setenv("LIBGL_GL", "21", 1);     // 模拟 OpenGL 2.1
            setenv("LIBGL_LOGERR", "1", 1);  // 记录错误
            setenv("LIBGL_DEBUG", "1", 1);   // 调试信息
            
            LOGI("✓ FNA renderer: OpenGL + gl4es AGL (Android, static-linked)");
        } else if (strcmp(g_renderer, "vulkan") == 0) {
            // Vulkan 渲染器（实验性）
            setenv("FNA3D_FORCE_DRIVER", "Vulkan", 1);
            LOGI("✓ FNA renderer: Vulkan (experimental)");
        } else {
            LOGW("Unknown renderer type: %s, using default", g_renderer);
        }
    } else {
        // 默认使用原生 OpenGL ES 3
        setenv("FNA3D_FORCE_DRIVER", "OpenGL", 1);
        setenv("FNA3D_OPENGL_FORCE_CORE_PROFILE", "0", 1);
        setenv("FNA3D_OPENGL_FORCE_ES3", "1", 1);
        setenv("FNA3D_OPENGL_FORCE_VER_MAJOR", "3", 1);           // 限制版本
        setenv("FNA3D_OPENGL_FORCE_VER_MINOR", "0", 1);
        setenv("FNA3D_OPENGL_FORCE_COMPATIBILITY_PROFILE", "1", 1);
        // 禁用不支持的高级OpenGL扩展
        setenv("FNA3D_DISABLE_ARB_DEBUG_OUTPUT", "1", 1);
        setenv("FNA3D_DISABLE_ARB_EXTENSION", "1", 1);
        setenv("SDL_VIDEO_GL_DRIVER", "", 1);
        setenv("SDL_VIDEO_X11_FORCE_EGL", "1", 1);
        setenv("SDL_OPENGL_ES_DRIVER", "1", 1);
        LOGI("✓ FNA renderer: Native OpenGL ES 3 (default)");
    }

    // 3. 预加载并初始化 .NET 加密库（需要 JNI）
    // 注：gl4es现在使用Gish方案（静态链接），在JNI_OnLoad中已经初始化
    char firstPath[1024] = {0};
    if (g_nativeSearchPaths) {
        const char* sep = strchr(g_nativeSearchPaths, ':');
        size_t len = sep ? (size_t)(sep - g_nativeSearchPaths) : strlen(g_nativeSearchPaths);
        if (len >= sizeof(firstPath)) len = sizeof(firstPath) - 1;
        memcpy(firstPath, g_nativeSearchPaths, len);
        firstPath[len] = '\0';
    }
    
    if (firstPath[0] != '\0') {
        char cryptoPath[1536];
        snprintf(cryptoPath, sizeof(cryptoPath), "%s/libSystem.Security.Cryptography.Native.Android.so", firstPath);
        
        LOGI("🔐 Attempting to preload crypto library: %s", cryptoPath);
        void* cryptoLib = dlopen(cryptoPath, RTLD_NOW | RTLD_GLOBAL);
        if (cryptoLib) {
            LOGI("✓ Crypto library loaded successfully");
            
            // 查找并调用 JNI_OnLoad 函数来初始化加密库
            typedef jint (*JNI_OnLoad_t)(JavaVM*, void*);
            JNI_OnLoad_t crypto_onload = (JNI_OnLoad_t)dlsym(cryptoLib, "JNI_OnLoad");
            if (crypto_onload) {
                JavaVM* jvm = Bridge_GetJavaVM();
                if (jvm) {
                    jint jni_version = crypto_onload(jvm, NULL);
                    LOGI("✓ Crypto library JNI initialized (version: 0x%x)", jni_version);
                } else {
                    LOGW("⚠️  JavaVM not available, crypto JNI not initialized");
                }
            } else {
                LOGI("ℹ️  Crypto library has no JNI_OnLoad (may not need it)");
            }
        } else {
            LOGW("⚠️  Failed to preload crypto library: %s", dlerror());
            LOGI("ℹ️  Will try to load it lazily via CoreCLR");
        }
    }
    
    char coreclrPath[1536];
    if (firstPath[0] != '\0') 
        snprintf(coreclrPath, sizeof(coreclrPath), "%s/libcoreclr.so", firstPath);
    else 
        snprintf(coreclrPath, sizeof(coreclrPath), "libcoreclr.so");
    
    // 5. 加载 CoreCLR 动态库
    void* coreclrLib = dlopen(coreclrPath, RTLD_LAZY | RTLD_LOCAL);
    if (!coreclrLib) { 
        LOGE("dlopen coreclr.so fail: %s", dlerror()); 
        return -11; 
    }
    
    // 6. 定义 CoreCLR API 函数指针类型
    typedef int (*coreclr_initialize_ptr)(const char*,const char*,int,const char**,const char**,void**,unsigned int*);
    typedef int (*coreclr_execute_assembly_ptr)(void*,unsigned int,int,const char**,const char*,unsigned int*);
    typedef int (*coreclr_shutdown_ptr)(void*,unsigned int);
    
    // 7. 获取 CoreCLR API 函数指针
    dlerror(); // 清除之前的错误
    coreclr_initialize_ptr coreclr_initialize = (coreclr_initialize_ptr)dlsym(coreclrLib, "coreclr_initialize");
    const char* err1 = dlerror();
    if (err1) LOGE("dlsym coreclr_initialize fail: %s", err1);
    
    coreclr_execute_assembly_ptr coreclr_execute_assembly = (coreclr_execute_assembly_ptr)dlsym(coreclrLib, "coreclr_execute_assembly");
    const char* err2 = dlerror();
    if (err2) LOGE("dlsym coreclr_execute_assembly fail: %s", err2);
    
    coreclr_shutdown_ptr coreclr_shutdown = (coreclr_shutdown_ptr)dlsym(coreclrLib, "coreclr_shutdown");
    const char* err3 = dlerror();
    if (err3) {
        LOGW("dlsym coreclr_shutdown fail: %s (可能在 .NET 7+ 中已移除，将跳过)", err3);
    }
    
    // 注意: coreclr_shutdown 在 .NET 7+ 中可能不存在，这是正常的
    if (!coreclr_initialize || !coreclr_execute_assembly) { 
        dlclose(coreclrLib); 
        LOGE("coreclr dlsym fail: init=%p, exec=%p, shutdown=%p", 
             coreclr_initialize, coreclr_execute_assembly, coreclr_shutdown); 
        return -12; 
    }
    
    if (coreclr_shutdown) {
        LOGI("CoreCLR shutdown function available");
    } else {
        LOGW("CoreCLR shutdown function not available (expected in .NET 7+)");
    }
    
    // 7. 准备 CoreCLR 初始化参数
    const char* keys[] = { 
        "TRUSTED_PLATFORM_ASSEMBLIES",      // 受信程序集列表
        "APP_PATHS",                        // 应用程序路径
        "APP_CONTEXT_BASE_DIRECTORY",       // 应用程序基础目录
        "NATIVE_DLL_SEARCH_DIRECTORIES"     // 原生 DLL 搜索目录
    };
    const char* vals[] = { 
        g_trustedAssemblies, 
        h_appDir, 
        h_appDir, 
        g_nativeSearchPaths 
    };
    
    // 7.5 初始化 JNI 环境（CoreCLR 在 Android 上需要 JNI）
    LOGI("Initializing JNI environment for CoreCLR...");
    JNIEnv* env = Bridge_GetJNIEnv();
    if (!env) {
        dlclose(coreclrLib);
        LOGE("Failed to get JNI environment");
        return -15;
    }
    LOGI("JNI environment initialized successfully at %p", env);
    
    // 8. 初始化 CoreCLR 运行时
    void* hostHandle; 
    unsigned int domainId;
    
    // 打印初始化参数以便调试
    LOGI("========== CoreCLR Initialization Parameters ==========");
    LOGI("Executable Path: %s", g_launcherDll);
    LOGI("App Domain Name: AppDomain");
    LOGI("Property Count: 4");
    for (int i = 0; i < 4; i++) {
        LOGI("  [%d] %s = %s", i, keys[i], vals[i] ? (strlen(vals[i]) > 200 ? "[too long to display]" : vals[i]) : "[NULL]");
    }
    LOGI("=======================================================");
    
    LOGI(">>> About to call coreclr_initialize...");
    int rc = coreclr_initialize(g_launcherDll, "AppDomain", 4, keys, vals, &hostHandle, &domainId);
    LOGI("<<< coreclr_initialize returned: %d", rc);
    
    if (rc != 0) { 
        dlclose(coreclrLib); 
        LOGE("coreclr_initialize fail: %d", rc); 
        return -13; 
    }
    
    // 8.5 注：TMLContentManagerPatch 已禁用
    // 原因：补丁依赖MonoMod，加载会导致CoreCLR断言失败
    // 当前策略：依赖已修改的System.Linq.dll（First()方法返回default而不是抛异常）
    LOGI("ℹ️  TMLContentManagerPatch disabled - relying on modified System.Linq.dll");
    
    // 9. 执行主程序集
    LOGI("🎮 Starting main game assembly...");
    unsigned int exitCode = 0;
    const char* argv[] = { h_appPath };
    rc = coreclr_execute_assembly(hostHandle, domainId, 1, argv, g_launcherDll, &exitCode);
    
    // 10. 关闭 CoreCLR 运行时（如果函数可用）
    if (coreclr_shutdown) {
        LOGI("Calling coreclr_shutdown");
        coreclr_shutdown(hostHandle, domainId);
    } else {
        LOGW("Skipping coreclr_shutdown (not available in this .NET version)");
    }
    
    // 11. 卸载 CoreCLR 动态库
    dlclose(coreclrLib);
    
    // 12. 返回退出码
    LOGI("CoreCLR execution finished with result: %d", rc == 0 ? (int)exitCode : -20);
    return rc == 0 ? (int)exitCode : -20;
}


