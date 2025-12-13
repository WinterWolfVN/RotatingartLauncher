/**
 * @file box64_launcher.cpp
 * @brief Box64 启动器实现
 * 
 * 参考 box64wine 项目的实现，提供通过 Box64 转译运行 x86_64 Linux 程序的功能
 */

#include "box64_launcher.h"
#include "app_logger.h"
#include "elfloader.h"  // GetEntryPoint, GetElfDelta
#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <dlfcn.h>
#include <cstdlib>
#include <cstring>
#include <errno.h>
#include <pthread.h>
#include <fcntl.h>
#include <dirent.h>
#include <sys/wait.h>
#include <ctime>
#include <string>
#include <vector>
#include <stdarg.h>

#define LOG_TAG "Box64Launcher"

// Box64 核心函数声明（C 函数，需要 extern "C"）
extern "C" {
#include "core.h"  // Box64 核心函数声明
}

static int (*box64_initialize)(int argc, const char **argv, char** env, x64emu_t** emulator, elfheader_t** elfheader, int exec) = initialize;
static int (*box64_emulate)(x64emu_t* emu, elfheader_t* elf_header) = emulate;
// GetEntryPoint 和 GetElfDelta 需要 lib_t* 类型，暂时设为 nullptr，如果需要再修复
static uintptr_t (*box64_GetEntryPoint)(void* maplib, elfheader_t* h) = nullptr;
static void* (*box64_GetElfDelta)(elfheader_t* h) = nullptr;
// box64_set_tmp_dir 在 wrappedlibc.c 中定义，需要外部声明
extern "C" void box64_set_tmp_dir(const char* tmp_dir);
static void (*box64_set_tmp_dir_ptr)(const char* tmp_dir) = box64_set_tmp_dir;

// Box64 库句柄（不再需要，因为直接链接）
static void* g_box64_handle = (void*)1;  // 设置为非空，表示已"加载"

// 加载 Box64 库（现在直接链接，不需要动态加载）
static bool load_box64_library(const char* nativeLibDir) {
    // Box64 现在直接链接到主库，不需要动态加载
    if (g_box64_handle != nullptr) {
        LOGI(LOG_TAG, "Box64 library already loaded (statically linked)");
        return true;
    }
    
    // 验证函数指针是否有效
    if (!box64_initialize || !box64_emulate) {
        LOGE(LOG_TAG, "Box64 functions not available (link error?)");
        return false;
    }
    
    LOGI(LOG_TAG, "Box64 library loaded successfully (statically linked with BOX32 support)");
    return true;
}

// 全局变量
static char g_data_dir[512] = {0};

// Pipe file descriptors for stdout/stderr redirection
static int g_stdout_pipe[2] = {-1, -1};
static int g_stderr_pipe[2] = {-1, -1};
static pthread_t g_stdout_thread = 0;
static pthread_t g_stderr_thread = 0;
static volatile bool g_redirect_running = false;
static FILE* g_log_file = nullptr;
static pthread_mutex_t g_log_mutex = PTHREAD_MUTEX_INITIALIZER;

// 输出重定向线程函数
static void* stdout_redirect_thread(void* arg) {
    char buffer[4096];
    ssize_t bytes_read;

    while (g_redirect_running) {
        bytes_read = read(g_stdout_pipe[0], buffer, sizeof(buffer) - 1);
        if (bytes_read > 0) {
            buffer[bytes_read] = '\0';
            if (bytes_read > 0 && buffer[bytes_read - 1] == '\n') {
                buffer[bytes_read - 1] = '\0';
            }
            LOGI(LOG_TAG, "%s", buffer);
            pthread_mutex_lock(&g_log_mutex);
            if (g_log_file) {
                fprintf(g_log_file, "[STDOUT] %s\n", buffer);
                fflush(g_log_file);
            }
            pthread_mutex_unlock(&g_log_mutex);
        } else if (bytes_read == 0 || (bytes_read < 0 && errno != EINTR)) {
            break;
        }
    }
    return nullptr;
}

static void* stderr_redirect_thread(void* arg) {
    char buffer[4096];
    ssize_t bytes_read;

    while (g_redirect_running) {
        bytes_read = read(g_stderr_pipe[0], buffer, sizeof(buffer) - 1);
        if (bytes_read > 0) {
            buffer[bytes_read] = '\0';
            if (bytes_read > 0 && buffer[bytes_read - 1] == '\n') {
                buffer[bytes_read - 1] = '\0';
            }
            if (strcasestr(buffer, "err:") || strcasestr(buffer, "error") ||
                strcasestr(buffer, "fatal") || strcasestr(buffer, "invalid")) {
                LOGW(LOG_TAG, "%s", buffer);
            } else {
                LOGI(LOG_TAG, "%s", buffer);
            }
            pthread_mutex_lock(&g_log_mutex);
            if (g_log_file) {
                fprintf(g_log_file, "[STDERR] %s\n", buffer);
                fflush(g_log_file);
            }
            pthread_mutex_unlock(&g_log_mutex);
        } else if (bytes_read == 0 || (bytes_read < 0 && errno != EINTR)) {
            break;
        }
    }
    return nullptr;
}

static void start_output_redirect() {
    if (g_redirect_running) return;

    if (pipe(g_stdout_pipe) < 0) {
        LOGE(LOG_TAG, "Failed to create stdout pipe: %s", strerror(errno));
        return;
    }
    if (pipe(g_stderr_pipe) < 0) {
        LOGE(LOG_TAG, "Failed to create stderr pipe: %s", strerror(errno));
        close(g_stdout_pipe[0]);
        close(g_stdout_pipe[1]);
        return;
    }

    dup2(g_stdout_pipe[1], STDOUT_FILENO);
    dup2(g_stderr_pipe[1], STDERR_FILENO);

    setvbuf(stdout, nullptr, _IOLBF, 0);
    setvbuf(stderr, nullptr, _IOLBF, 0);

    // 打开日志文件
    if (g_data_dir[0] != '\0') {
        time_t now = time(nullptr);
        char* time_str = ctime(&now);
        
        char log_path[1024];
        snprintf(log_path, sizeof(log_path), "%s/box64_output.log", g_data_dir);
        unlink(log_path);
        pthread_mutex_lock(&g_log_mutex);
        g_log_file = fopen(log_path, "w");
        if (g_log_file) {
            fprintf(g_log_file, "\n========== Log started at %s", time_str);
            fflush(g_log_file);
            LOGI(LOG_TAG, "Log file opened: %s", log_path);
        } else {
            LOGW(LOG_TAG, "Failed to open log file: %s (%s)", log_path, strerror(errno));
        }
        pthread_mutex_unlock(&g_log_mutex);
    }

    g_redirect_running = true;

    pthread_create(&g_stdout_thread, nullptr, stdout_redirect_thread, nullptr);
    pthread_create(&g_stderr_thread, nullptr, stderr_redirect_thread, nullptr);

    LOGI(LOG_TAG, "Started stdout/stderr redirect to logcat");
}

static void stop_output_redirect() {
    if (!g_redirect_running) return;

    g_redirect_running = false;

    if (g_stdout_pipe[1] >= 0) close(g_stdout_pipe[1]);
    if (g_stderr_pipe[1] >= 0) close(g_stderr_pipe[1]);

    if (g_stdout_thread) pthread_join(g_stdout_thread, nullptr);
    if (g_stderr_thread) pthread_join(g_stderr_thread, nullptr);

    if (g_stdout_pipe[0] >= 0) close(g_stdout_pipe[0]);
    if (g_stderr_pipe[0] >= 0) close(g_stderr_pipe[0]);

    g_stdout_pipe[0] = g_stdout_pipe[1] = -1;
    g_stderr_pipe[0] = g_stderr_pipe[1] = -1;
    g_stdout_thread = g_stderr_thread = 0;

    pthread_mutex_lock(&g_log_mutex);
    if (g_log_file) {
        time_t now = time(nullptr);
        char* time_str = ctime(&now);
        fprintf(g_log_file, "========== Log ended at %s\n", time_str);
        fclose(g_log_file);
        g_log_file = nullptr;
        LOGI(LOG_TAG, "Log file closed");
    }
    pthread_mutex_unlock(&g_log_mutex);

    LOGI(LOG_TAG, "Stopped stdout/stderr redirect");
}

// 设置基本目录结构（简化版，不需要完整的 rootfs）
static void setup_basic_dirs(const char* dataDir) {
    // 只创建必要的目录
    std::string tmpDir = std::string(dataDir) + "/tmp";
    mkdir(tmpDir.c_str(), 0755);
    
    LOGI(LOG_TAG, "Created basic directories");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_app_ralaunch_core_GameLauncher_initBox64(
    JNIEnv* env,
    jobject /* this */,
    jstring dataDir,
    jstring nativeLibDir) {
    
    const char* dataDirStr = env->GetStringUTFChars(dataDir, nullptr);
    const char* nativeLibDirStr = env->GetStringUTFChars(nativeLibDir, nullptr);
    
    strncpy(g_data_dir, dataDirStr, sizeof(g_data_dir) - 1);
    
    LOGI(LOG_TAG, "Initializing Box64...");
    LOGI(LOG_TAG, "Data directory: %s", dataDirStr);
    LOGI(LOG_TAG, "Native lib directory: %s", nativeLibDirStr);
    
    // 切换到数据目录
    if(chdir(dataDirStr) != 0) {
        LOGE(LOG_TAG, "Failed to chdir to data directory: %s", strerror(errno));
    } else {
        LOGI(LOG_TAG, "Changed working directory to: %s", dataDirStr);
    }
    
    // 设置基本目录结构
    setup_basic_dirs(dataDirStr);
    
    // 设置 Box64 环境变量（简化版，只保留必要的）
    setenv("BOX64_LOG", "1", 1);       // 0=NONE,1=INFO,2=DEBUG,3=DUMP
    setenv("BOX64_DYNAREC", "1", 1);   // 启用动态重编译
    setenv("BOX64_NORCFILES", "1", 1); // 禁用 RC 文件
    
    // Locale 环境
    setenv("LANG", "en_US.UTF-8", 1);
    
    // Temp 目录
    std::string tmpDir = std::string(dataDirStr) + "/tmp";
    mkdir(tmpDir.c_str(), 0755);
    setenv("TMPDIR", tmpDir.c_str(), 1);
    setenv("TMP", tmpDir.c_str(), 1);
    
    // PATH
    std::string pathEnv = std::string(dataDirStr) + "/bin:" +
                          "/system/bin:/system/xbin:/vendor/bin";
    setenv("PATH", pathEnv.c_str(), 1);
    LOGI(LOG_TAG, "PATH=%s", pathEnv.c_str());
    
    // Library paths - x64lib 目录包含 Box64 需要的 x86_64 和 i386 库
    // 库文件在 x64lib/box64-x86_64-linux-gnu/ 和 x64lib/box64-i386-linux-gnu/ 目录下
    std::string x64LibDir = std::string(dataDirStr) + "/x64lib";
    std::string box64LibSubDir64 = x64LibDir + "/box64-x86_64-linux-gnu";
    std::string box64LibSubDir32 = x64LibDir + "/box64-i386-linux-gnu";
    // 同时包含 64 位和 32 位库路径，Box64 会根据程序架构自动选择
    std::string libPath = box64LibSubDir64 + ":" + box64LibSubDir32 + ":" + x64LibDir;
    setenv("BOX64_LD_LIBRARY_PATH", libPath.c_str(), 1);
    // 也设置 LD_LIBRARY_PATH 以便程序能找到库
    setenv("LD_LIBRARY_PATH", libPath.c_str(), 1);
    LOGI(LOG_TAG, "BOX64_LD_LIBRARY_PATH=%s", libPath.c_str());
    LOGI(LOG_TAG, "LD_LIBRARY_PATH=%s", libPath.c_str());
    LOGI(LOG_TAG, "x64lib directory: %s", x64LibDir.c_str());
    
    // 加载 Box64 库
    if (!load_box64_library(nativeLibDirStr)) {
        LOGE(LOG_TAG, "Failed to load Box64 library");
        env->ReleaseStringUTFChars(dataDir, dataDirStr);
        env->ReleaseStringUTFChars(nativeLibDir, nativeLibDirStr);
        return JNI_FALSE;
    }
    
    // 设置 Box64 /tmp 路径重定向
    if (box64_set_tmp_dir_ptr) {
        box64_set_tmp_dir_ptr(tmpDir.c_str());
        LOGI(LOG_TAG, "Box64 /tmp redirection set to: %s", tmpDir.c_str());
    }
    
    env->ReleaseStringUTFChars(dataDir, dataDirStr);
    env->ReleaseStringUTFChars(nativeLibDir, nativeLibDirStr);
    
    LOGI(LOG_TAG, "Box64 initialized successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_app_ralaunch_core_GameLauncher_runBox64(
    JNIEnv* env,
    jobject /* this */,
    jobjectArray args) {
    
    int argc = env->GetArrayLength(args);
    if (argc == 0) {
        LOGE(LOG_TAG, "No arguments provided");
        return -1;
    }
    
    LOGI(LOG_TAG, "Running Box64 with %d arguments", argc);
    
    // 获取第一个参数（可执行文件路径）并设置工作目录
    jstring firstArg = (jstring) env->GetObjectArrayElement(args, 0);
    const char* firstArgStr = env->GetStringUTFChars(firstArg, nullptr);
    
    // 提取目录路径
    std::string exePath(firstArgStr);
    std::string workDir;
    std::string relativeExePath;
    
    // 特殊处理：如果路径包含 "steamcmd/linux32"，将工作目录设置为 steamcmd 目录
    // 因为 SteamCMD 需要从 steamcmd 目录运行，而不是 linux32 子目录
    size_t steamcmdPos = exePath.find("/steamcmd/");
    if (steamcmdPos != std::string::npos) {
        size_t steamcmdEnd = steamcmdPos + strlen("/steamcmd");
        workDir = exePath.substr(0, steamcmdEnd);  // 包含 /steamcmd
        relativeExePath = "linux32/steamcmd";  // 使用相对路径
        LOGI(LOG_TAG, "Detected SteamCMD, setting working directory to: %s", workDir.c_str());
        LOGI(LOG_TAG, "Using relative path: %s", relativeExePath.c_str());
    } else {
        // 普通情况：从可执行文件路径提取工作目录
        size_t lastSlash = exePath.find_last_of('/');
        if (lastSlash != std::string::npos) {
            workDir = exePath.substr(0, lastSlash);
            relativeExePath = exePath.substr(lastSlash + 1);  // 只保留文件名
        } else {
            workDir = ".";  // 当前目录
            relativeExePath = exePath;
        }
    }
    
    // 设置工作目录
    if (!workDir.empty()) {
        if (chdir(workDir.c_str()) == 0) {
            LOGI(LOG_TAG, "Changed working directory to: %s", workDir.c_str());
        } else {
            LOGW(LOG_TAG, "Failed to change working directory to: %s (%s)", workDir.c_str(), strerror(errno));
        }
    }
    
    // 更新第一个参数为相对路径（如果适用）
    if (!relativeExePath.empty() && relativeExePath != exePath) {
        // 释放原来的字符串
        env->ReleaseStringUTFChars(firstArg, firstArgStr);
        // 创建新的相对路径字符串
        jstring newFirstArg = env->NewStringUTF(relativeExePath.c_str());
        env->SetObjectArrayElement(args, 0, newFirstArg);
        firstArgStr = env->GetStringUTFChars(newFirstArg, nullptr);
        LOGI(LOG_TAG, "Updated executable path to relative: %s", relativeExePath.c_str());
    }
    
    env->ReleaseStringUTFChars(firstArg, firstArgStr);
    
    start_output_redirect();
    
    const char** argv = new const char*[argc + 2];
    char** argv_allocated = new char*[argc + 1];
    argv[0] = "box64";
    
    for (int i = 0; i < argc; i++) {
        jstring jstr = (jstring) env->GetObjectArrayElement(args, i);
        const char* str = env->GetStringUTFChars(jstr, nullptr);
        argv_allocated[i] = strdup(str);
        argv[i + 1] = argv_allocated[i];
        LOGI(LOG_TAG, "argv[%d] = %s", i + 1, argv[i + 1]);
        env->ReleaseStringUTFChars(jstr, str);
    }
    argv[argc + 1] = nullptr;
    
    extern char** environ;
    
    x64emu_t* emu = nullptr;
    elfheader_t* elf_header = nullptr;
    
    // 确保 Box64 库已加载
    if (g_box64_handle == nullptr || !box64_initialize || !box64_emulate) {
        LOGE(LOG_TAG, "Box64 library not loaded");
        for (int i = 0; i < argc; i++) {
            free(argv_allocated[i]);
        }
        delete[] argv_allocated;
        delete[] argv;
        stop_output_redirect();
        return -1;
    }
    
    LOGI(LOG_TAG, "Calling box64 initialize...");
    int init_result = box64_initialize(argc + 1, argv, environ, &emu, &elf_header, 1);
    
    if (init_result != 0) {
        LOGE(LOG_TAG, "Box64 initialize failed with code: %d", init_result);
        for (int i = 0; i < argc; i++) {
            free(argv_allocated[i]);
        }
        delete[] argv_allocated;
        delete[] argv;
        stop_output_redirect();
        return init_result;
    }
    
    // Get entry point info for debugging (可选，GetEntryPoint 需要 lib_t* 参数)
    // 暂时注释掉，如果需要调试信息可以启用
    // if (box64_GetElfDelta) {
    //     void* delta = box64_GetElfDelta((elfheader_t*)elf_header);
    //     LOGI(LOG_TAG, "ELF Delta: %p", delta);
    // }
    
    LOGI(LOG_TAG, "Calling box64 emulate...");
    int result = box64_emulate(emu, elf_header);
    LOGI(LOG_TAG, "Box64 emulate returned: %d", result);
    
    fflush(stdout);
    fflush(stderr);
    
    for (int i = 0; i < argc; i++) {
        free(argv_allocated[i]);
    }
    delete[] argv_allocated;
    delete[] argv;
    
    stop_output_redirect();
    
    return result;
}

