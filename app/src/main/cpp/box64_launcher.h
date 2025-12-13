/**
 * @file box64_launcher.h
 * @brief Box64 启动器头文件
 * 
 * 提供通过 Box64 转译运行 x86_64 Linux 程序的接口
 */

#ifndef BOX64_LAUNCHER_H
#define BOX64_LAUNCHER_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Box64 初始化
 * 
 * @param env JNI 环境
 * @param dataDir 数据目录路径
 * @param nativeLibDir native 库目录路径
 * @return JNI_TRUE 成功，JNI_FALSE 失败
 */
JNIEXPORT jboolean JNICALL
Java_com_app_ralaunch_core_GameLauncher_initBox64(
    JNIEnv* env,
    jobject thiz,
    jstring dataDir,
    jstring nativeLibDir);

/**
 * 通过 Box64 运行程序
 * 
 * @param env JNI 环境
 * @param args 命令行参数数组
 * @return 程序退出码
 */
JNIEXPORT jint JNICALL
Java_com_app_ralaunch_core_GameLauncher_runBox64(
    JNIEnv* env,
    jobject thiz,
    jobjectArray args);

#ifdef __cplusplus
}
#endif

#endif // BOX64_LAUNCHER_H

