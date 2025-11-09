/**
 * @file jni_bridge.c
 * @brief JNI 桥接器实现
 */

#include <android/log.h>
#include <string.h>
#include <stdlib.h>
#include "jni_bridge.h"

#define LOG_TAG "GameLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/** 全局 JavaVM 指针 */
static JavaVM* g_jvm = NULL;

/** 标记当前线程是否由此模块附加到 JVM */
static int g_threadAttached = 0;

/** 游戏性能数据（C#侧更新，Java侧读取） */
static float g_gameFps = 0.0f;
static float g_managedMemoryMB = 0.0f;
static int g_gcGen0Count = 0;
static int g_gcGen1Count = 0;
static int g_gcGen2Count = 0;

/**
 * @brief JNI_OnLoad 生命周期回调实现
 * 
 * @param vm JavaVM 指针
 * @return JNI_VERSION_1_6
 * 
 * 保存 JavaVM 指针供后续使用。
 */
jint Bridge_JNI_OnLoad(JavaVM* vm) {
    LOGI("JNI_OnLoad called");
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

/**
 * @brief JNI_OnUnload 生命周期回调实现
 * 
 * @param vm JavaVM 指针
 * 
 * 清理所有全局资源和内存。
 */
void Bridge_JNI_OnUnload(JavaVM* vm) {
    (void)vm;
    LOGI("JNI_OnUnload called");
    // CleanupGlobalMemory() 已移除（旧的 dotnet_host 代码）
    g_jvm = NULL;
}

/**
 * @brief 获取当前线程的 JNI 环境
 * 
 * @return JNIEnv 指针，失败返回 NULL
 * 
 * 如果当前线程未附加到 JVM（JNI_EDETACHED），会自动附加。
 * 自动附加的线程需要调用 Bridge_SafeDetachJNIEnv() 分离。
 */
JNIEnv* Bridge_GetJNIEnv() {
    if (g_jvm == NULL) { 
        LOGE("JavaVM is NULL in GetJNIEnv"); 
        return NULL; 
    }
    
    JNIEnv* env = NULL;
    jint result = (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6);
    
    if (result == JNI_EDETACHED) {
        // 线程未附加，执行附加操作
        LOGI("Current thread not attached, attaching now...");
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) { 
            LOGE("Failed to attach current thread to JVM"); 
            return NULL; 
        }
        g_threadAttached = 1;
    } else if (result != JNI_OK) { 
        LOGE("Failed to get JNIEnv, error code: %d", result); 
        return NULL; 
    }
    
    return env;
}

/**
 * @brief 安全地从 JVM 分离当前线程
 * 
 * 仅当线程是通过 Bridge_GetJNIEnv() 附加时才执行分离操作。
 * 这避免了对 Java 创建的线程进行错误的分离操作。
 */
void Bridge_SafeDetachJNIEnv() {
    if (g_jvm != NULL && g_threadAttached) {
        JNIEnv* env = NULL;
        jint result = (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6);
        if (result == JNI_OK) {
            (*g_jvm)->DetachCurrentThread(g_jvm);
            g_threadAttached = 0;
            LOGI("Thread safely detached from JVM");
        }
    }
}

/**
 * @brief 获取全局 JavaVM 指针
 * 
 * @return JavaVM 指针，如果未初始化则返回 NULL
 * 
 * 用于其他模块（如 .NET 加密库）获取 JavaVM 以初始化 JNI 环境。
 */
JavaVM* Bridge_GetJavaVM() {
    return g_jvm;
}

/**
 * @brief 通知 Java 层游戏已退出
 * 
 * @param exitCode 游戏退出码
 * 
 * 通过 JNI 调用 GameActivity.onGameExit(int) 静态方法。
 * 如果方法不存在或调用失败，会静默失败（不抛出异常）。
 */
void Bridge_NotifyGameExit(int exitCode) {
    JNIEnv* env = Bridge_GetJNIEnv();
    if (!env) return;
    
    // 查找 GameActivity 类
    jclass clazz = (*env)->FindClass(env, "com/app/ralaunch/activity/GameActivity");
    if (clazz) {
        // 查找 onGameExit 静态方法
        jmethodID method = (*env)->GetStaticMethodID(env, clazz, "onGameExit", "(I)V");
        if (method) {
            // 调用静态方法
            (*env)->CallStaticVoidMethod(env, clazz, method, exitCode);
        }
        (*env)->DeleteLocalRef(env, clazz);
    }
}

// ======================================================================
// 已移除的旧 JNI 函数（已在 Java 端和 C++ 端删除）：
// - setLaunchParams
// - setLaunchParamsWithRuntime  
// - setVerboseLogging
// - setRenderer
// - setBootstrapLaunchParams
// ======================================================================

/**
 * @brief 获取Native层的真实CPU架构
 * 
 * @param env JNI环境指针
 * @param clazz Java类引用
 * @return CPU架构字符串："arm64", "x86_64", "arm", "x86", 或 "unknown"
 * 
 * 此函数在Native层通过编译时宏直接检测当前进程的真实CPU架构，
 * 比Java层的Build.SUPPORTED_ABIS更可靠，尤其是在使用ARM翻译层的x86模拟器上。
 */
JNIEXPORT jstring JNICALL Java_com_app_ralaunch_utils_RuntimePreference_getNativeArchitecture(
    JNIEnv* env, jclass clazz) {
    (void)clazz; // 未使用的参数
    
    #if defined(__aarch64__) || defined(__arm64__)
        LOGI("Native architecture detected: arm64");
        return (*env)->NewStringUTF(env, "arm64");
    #elif defined(__x86_64__) || defined(__amd64__)
        LOGI("Native architecture detected: x86_64");
        return (*env)->NewStringUTF(env, "x86_64");
    #elif defined(__arm__)
        LOGI("Native architecture detected: arm");
        return (*env)->NewStringUTF(env, "arm");
    #elif defined(__i386__)
        LOGI("Native architecture detected: x86");
        return (*env)->NewStringUTF(env, "x86");
    #else
        LOGE("Native architecture UNKNOWN!");
        return (*env)->NewStringUTF(env, "unknown");
    #endif
}

/**
 * ═══════════════════════════════════════════════════════════════
 * 游戏性能数据接口（供C#和Java双向通信）
 * ═══════════════════════════════════════════════════════════════
 */

/**
 * @brief [C#调用] 更新游戏性能数据
 * 
 * C#侧定期调用此方法更新性能数据，Java侧通过getter读取
 * 
 * @param fps 当前FPS
 * @param managedMemoryMB C#托管内存（MB）
 * @param gen0 GC Gen0计数
 * @param gen1 GC Gen1计数
 * @param gen2 GC Gen2计数
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_utils_PerformanceMonitor_updateGamePerformanceNative(
    JNIEnv* env, jclass clazz,
    jfloat fps, jfloat managedMemoryMB,
    jint gen0, jint gen1, jint gen2) {
    (void)env;
    (void)clazz;
    
    g_gameFps = fps;
    g_managedMemoryMB = managedMemoryMB;
    g_gcGen0Count = gen0;
    g_gcGen1Count = gen1;
    g_gcGen2Count = gen2;
}

/**
 * @brief [Java调用] 获取C#游戏真实FPS
 * 
 * @return 游戏真实渲染帧率
 */
JNIEXPORT jfloat JNICALL
Java_com_app_ralaunch_utils_PerformanceMonitor_getGameFpsNative(
    JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    return g_gameFps;
}

/**
 * @brief [Java调用] 获取C#托管内存（MB）
 * 
 * @return C#托管内存大小（MB）
 */
JNIEXPORT jfloat JNICALL
Java_com_app_ralaunch_utils_PerformanceMonitor_getManagedMemoryNative(
    JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    return g_managedMemoryMB;
}

/**
 * @brief [Java调用] 获取GC统计信息
 * 
 * @return int数组 [Gen0, Gen1, Gen2]
 */
JNIEXPORT jintArray JNICALL
Java_com_app_ralaunch_utils_PerformanceMonitor_getGCStatsNative(
    JNIEnv* env, jclass clazz) {
    (void)clazz;
    
    jintArray result = (*env)->NewIntArray(env, 3);
    if (result == NULL) {
        return NULL;
    }
    
    jint stats[3] = { g_gcGen0Count, g_gcGen1Count, g_gcGen2Count };
    (*env)->SetIntArrayRegion(env, result, 0, 3, stats);
    
    return result;
}

/**
 * @brief [C#调用] P/Invoke wrapper - 更新游戏性能数据
 * 
 * C#通过P/Invoke调用此函数，避免直接操作JNI
 * 
 * @param fps 当前FPS
 * @param managedMemoryMB C#托管内存（MB）
 * @param gen0 GC Gen0计数
 * @param gen1 GC Gen1计数
 * @param gen2 GC Gen2计数
 */
#ifdef __cplusplus
extern "C" {
#endif

__attribute__((visibility("default")))
JNIEXPORT void JNICALL UpdateGamePerformance(
    float fps, float managedMemoryMB,
    int gen0, int gen1, int gen2) {
    // 更新全局变量
    g_gameFps = fps;
    g_managedMemoryMB = managedMemoryMB;
    g_gcGen0Count = gen0;
    g_gcGen1Count = gen1;
    g_gcGen2Count = gen2;
    
    // 调试日志（仅在FPS>0时打印）
    if (fps > 0) {
        LOGI("[PerformanceReporter] C# -> Native: FPS=%.1f Memory=%.1fMB GC(Gen0=%d Gen1=%d Gen2=%d)", 
             fps, managedMemoryMB, gen0, gen1, gen2);
    }
}

#ifdef __cplusplus
}
#endif

// ============================================================================
// 图标提取器JNI函数
// ============================================================================


