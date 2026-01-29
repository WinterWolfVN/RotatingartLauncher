/*
 * RALCore 虚拟鼠标范围限制
 * 
 * 限制虚拟鼠标在屏幕上的移动范围（用于游戏控制）
 */

#ifndef RAL_MOUSE_RANGE_H
#define RAL_MOUSE_RANGE_H

#include "SDL.h"

#ifdef __cplusplus
extern "C" {
#endif

/* ============================================================================
 * 虚拟鼠标范围控制
 * ============================================================================ */

/**
 * 启用/禁用虚拟鼠标范围限制
 */
DECLSPEC void SDLCALL RAL_SetVirtualMouseRangeEnabled(SDL_bool enabled);

/**
 * 设置屏幕尺寸（用于计算范围）
 */
DECLSPEC void SDLCALL RAL_SetVirtualMouseScreenSize(int width, int height);

/**
 * 设置虚拟鼠标范围
 * 
 * 参数为相对于屏幕中心的比例值（0.0 - 1.0）
 * 
 * @param left 左侧范围 (1.0 = 到屏幕左边缘)
 * @param top 上方范围 (1.0 = 到屏幕上边缘)
 * @param right 右侧范围 (1.0 = 到屏幕右边缘)
 * @param bottom 下方范围 (1.0 = 到屏幕下边缘)
 */
DECLSPEC void SDLCALL RAL_SetVirtualMouseRange(float left, float top, float right, float bottom);

/**
 * 应用范围限制到坐标
 * 
 * 如果范围限制启用，会修改传入的坐标值
 */
DECLSPEC void SDLCALL RAL_ApplyVirtualMouseRangeLimit(int *mouseX, int *mouseY);

/* 向后兼容：映射到 SDL 前缀的导出函数 */
#define SDL_SetVirtualMouseRangeEnabled RAL_SetVirtualMouseRangeEnabled
#define SDL_SetVirtualMouseScreenSize RAL_SetVirtualMouseScreenSize
#define SDL_SetVirtualMouseRange RAL_SetVirtualMouseRange
#define SDL_ApplyVirtualMouseRangeLimit RAL_ApplyVirtualMouseRangeLimit

#ifdef __cplusplus
}
#endif

#endif /* RAL_MOUSE_RANGE_H */
