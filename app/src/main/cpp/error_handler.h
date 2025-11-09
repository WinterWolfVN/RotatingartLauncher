/**
 * @file error_handler.h
 * @brief Error handling and crash reporting for CoreCLR runtime
 * 
 * 参考 CoreHost (D:\runtime-release-8.0\src\native\corehost) 的错误处理机制
 */

#pragma once

#include <signal.h>
#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @brief 初始化错误处理器
 * 
 * 设置 signal handler 来捕获崩溃信息并输出到 logcat
 */
void error_handler_init(void);

/**
 * @brief 设置 JNI 环境（用于错误报告）
 */
void error_handler_set_jni_env(JNIEnv* env, JavaVM* vm);

/**
 * @brief 记录详细的崩溃信息到 logcat
 */
void error_handler_log_crash(int sig, siginfo_t* info, void* context);

/**
 * @brief 禁用错误处理器
 */
void error_handler_cleanup(void);

#ifdef __cplusplus
}
#endif

