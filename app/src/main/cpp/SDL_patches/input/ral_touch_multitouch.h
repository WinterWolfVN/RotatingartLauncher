/*
 * RALCore 多点触控转鼠标
 * 
 * 允许多个手指同时生成独立的鼠标按下/释放事件
 */

#ifndef RAL_TOUCH_MULTITOUCH_H
#define RAL_TOUCH_MULTITOUCH_H

#include "SDL.h"

#ifdef __cplusplus
extern "C" {
#endif

/* 最大同时追踪的手指数量 */
#define RAL_MAX_TRACKED_FINGERS 10

/* ============================================================================
 * 多点触控状态管理
 * ============================================================================ */

/**
 * 检查多点触控模式是否启用
 * 
 * 通过环境变量 SDL_TOUCH_MOUSE_MULTITOUCH=1 启用
 */
SDL_bool RAL_IsMultitouchEnabled(void);

/**
 * 添加追踪的手指
 */
void RAL_AddMultitouchFinger(SDL_FingerID fingerid, float x, float y);

/**
 * 移除追踪的手指
 */
void RAL_RemoveMultitouchFinger(SDL_FingerID fingerid);

/**
 * 更新手指位置
 */
void RAL_UpdateMultitouchFinger(SDL_FingerID fingerid, float x, float y);

/**
 * 获取当前活跃手指 ID
 * 
 * 活跃手指控制鼠标光标移动
 */
SDL_FingerID RAL_GetActiveMultitouchFinger(void);

/**
 * 设置活跃手指
 */
void RAL_SetActiveMultitouchFinger(SDL_FingerID fingerid);

/**
 * 获取追踪的手指数量
 */
int RAL_GetMultitouchFingerCount(void);

/* ============================================================================
 * 虚拟控件触摸点管理
 * 
 * 被虚拟控件（如摇杆）占用的触摸点不应转换为鼠标事件
 * ============================================================================ */

/**
 * 标记触摸点被占用
 */
void RAL_ConsumeFingerTouch(int fingerId);

/**
 * 释放触摸点
 */
void RAL_ReleaseFingerTouch(int fingerId);

/**
 * 清除所有占用
 */
void RAL_ClearConsumedFingers(void);

/**
 * 检查触摸点是否被占用
 */
SDL_bool RAL_IsFingerConsumed(int fingerId);

#ifdef __cplusplus
}
#endif

#endif /* RAL_TOUCH_MULTITOUCH_H */
