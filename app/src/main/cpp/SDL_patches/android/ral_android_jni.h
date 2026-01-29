/*
 * RALCore Android JNI 扩展
 * 
 * 提供额外的 JNI 接口供 Java 层调用
 */

#ifndef RAL_ANDROID_JNI_H
#define RAL_ANDROID_JNI_H

#include <jni.h>
#include "SDL.h"

#ifdef __cplusplus
extern "C" {
#endif

/* ============================================================================
 * JNI 初始化
 * ============================================================================ */

/**
 * 注册 RALCore 扩展的 JNI 方法
 * 
 * 应在 SDL JNI 初始化后调用
 * 
 * @param env JNI 环境
 * @param activity_class SDLActivity 的 Java 类
 * @param controller_class SDLControllerManager 的 Java 类
 * @return 0 成功, -1 失败
 */
int RAL_JNI_RegisterMethods(JNIEnv *env, jclass activity_class, jclass controller_class);

/* ============================================================================
 * 鼠标直接控制
 * 
 * 绕过 SDL 内部状态跟踪，用于虚拟控件
 * ============================================================================ */

/**
 * 直接发送鼠标事件 (不检查状态)
 */
void RAL_OnMouseDirect(SDL_Window *window, int button, int action, float x, float y, SDL_bool relative);

/**
 * 直接发送鼠标按钮事件 (指定位置)
 */
void RAL_OnMouseButtonDirect(SDL_Window *window, int sdlButton, int pressed, float x, float y);

/**
 * 只发送鼠标按钮事件 (不移动光标)
 */
void RAL_OnMouseButtonOnly(SDL_Window *window, int sdlButton, int pressed);

/**
 * 获取当前鼠标位置
 */
int RAL_GetMouseStateX(void);
int RAL_GetMouseStateY(void);

/* ============================================================================
 * 触摸点管理
 * 
 * 允许虚拟控件"占用"触摸点，防止其被转换为鼠标事件
 * ============================================================================ */

/**
 * 标记触摸点被虚拟控件占用
 * 
 * 被占用的触摸点不会转换为鼠标事件
 * 
 * @param fingerId 触摸点 ID
 */
void RAL_ConsumeFingerTouch(int fingerId);

/**
 * 释放触摸点占用
 * 
 * @param fingerId 触摸点 ID
 */
void RAL_ReleaseFingerTouch(int fingerId);

/**
 * 清除所有占用的触摸点
 */
void RAL_ClearConsumedFingers(void);

/**
 * 检查触摸点是否被占用
 * 
 * @param fingerId 触摸点 ID
 * @return SDL_TRUE 如果被占用
 */
SDL_bool RAL_IsFingerConsumed(int fingerId);

/* ============================================================================
 * JNI 线程管理
 * ============================================================================ */

/**
 * 设置当前线程的 JNI 环境
 * 
 * 用于 Box64 等需要在非 Java 线程中使用 JNI 的场景
 */
int RAL_JNI_SetEnvCurrent(JNIEnv *env);

/**
 * 清除当前线程的 JNI 环境
 */
int RAL_JNI_SetEnvNull(void);

/* ============================================================================
 * 手柄震动
 * ============================================================================ */

/**
 * 手柄震动 (支持双马达)
 * 
 * @param device_id 设备 ID
 * @param low_frequency_intensity 低频马达强度 (0.0 - 1.0)
 * @param high_frequency_intensity 高频马达强度 (0.0 - 1.0)
 * @param duration_ms 持续时间 (毫秒)
 */
void RAL_JNI_HapticRumble(int device_id, float low_frequency_intensity, 
                           float high_frequency_intensity, int duration_ms);

#ifdef __cplusplus
}
#endif

#endif /* RAL_ANDROID_JNI_H */
