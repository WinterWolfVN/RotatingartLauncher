/*
 * RALCore SDL 配置头文件
 * 
 * 控制所有 RALCore 对 SDL 的扩展功能
 */

#ifndef RAL_SDL_CONFIG_H
#define RAL_SDL_CONFIG_H

/* ============================================================================
 * 功能开关
 * ============================================================================ */

/* 平台伪装：SDL_GetPlatform() 返回 "Linux" 而不是 "Android" */
#define RAL_SPOOF_PLATFORM_LINUX 1

/* 禁用断言时的窗口最小化 */
#define RAL_DISABLE_ASSERT_MINIMIZE 1

/* 启用虚拟鼠标范围限制功能 */
#define RAL_VIRTUAL_MOUSE_RANGE 1

/* 启用多点触控转鼠标 */
#define RAL_MULTITOUCH_MOUSE 1

/* 启用虚拟控件触摸点过滤 */
#define RAL_VIRTUAL_CONTROL_TOUCH_FILTER 1

/* 启用手柄震动扩展 */
#define RAL_JOYSTICK_RUMBLE 1

/* 启用动态渲染器加载 */
#define RAL_DYNAMIC_RENDERER 1

/* 启用 OSMesa/Zink 支持 */
#define RAL_OSMESA_SUPPORT 1

/* 启用额外显示模式 */
#define RAL_EXTRA_DISPLAY_MODES 1

/* 强制窗口全屏 */
#define RAL_FORCE_FULLSCREEN_WINDOW 1

/* AAudio 低延迟由环境变量控制 */
#define RAL_AAUDIO_OPTIONAL_LOW_LATENCY 1

/* ============================================================================
 * SDL3 兼容性宏
 * ============================================================================ */

/* SDL3 中这些 API 可能有变化，这里预留兼容层 */
#ifdef SDL_VERSION_ATLEAST
#if SDL_VERSION_ATLEAST(3, 0, 0)
    #define RAL_SDL3 1
    /* SDL3 使用新的事件系统 */
    #define RAL_USE_NEW_EVENT_SYSTEM 1
#else
    #define RAL_SDL2 1
#endif
#else
    #define RAL_SDL2 1
#endif

/* ============================================================================
 * 环境变量名称
 * ============================================================================ */

/* 多点触控开关 */
#define RAL_ENV_MULTITOUCH "SDL_TOUCH_MOUSE_MULTITOUCH"

/* AAudio 低延迟 */
#define RAL_ENV_AAUDIO_LOW_LATENCY "SDL_AAUDIO_LOW_LATENCY"

/* 渲染器选择 */
#define RAL_ENV_RENDERER "RALCORE_RENDERER"
#define RAL_ENV_EGL "RALCORE_EGL"

/* OpenGL 库路径 */
#define RAL_ENV_OPENGL_LIBRARY "FNA3D_OPENGL_LIBRARY"
#define RAL_ENV_OPENGL_DRIVER "FNA3D_OPENGL_DRIVER"

#endif /* RAL_SDL_CONFIG_H */
