/*
 * RALCore Android 动态渲染器
 * 
 * 支持运行时加载不同的 OpenGL 实现:
 * - native: 系统默认 EGL/GLES
 * - gl4es: OpenGL 2.1 翻译层
 * - angle: OpenGL ES over Vulkan
 * - zink: OpenGL over Vulkan (via OSMesa)
 * - dxvk: D3D11 over Vulkan
 */

#ifndef RAL_ANDROID_RENDERER_H
#define RAL_ANDROID_RENDERER_H

#include "SDL.h"
#include "../SDL_sysvideo.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 渲染器后端配置
 */
typedef struct {
    const char *name;           /* 渲染器名称 */
    const char *egl_library;    /* EGL 库路径 (NULL = 系统默认) */
    const char *gles_library;   /* GLES 库路径 (NULL = 系统默认) */
    SDL_bool need_preload;      /* 是否需要预加载 */
} RAL_RendererBackend;

/**
 * 加载渲染器
 * 
 * 根据名称加载对应的渲染器库
 * 
 * @param renderer_name 渲染器名称 (native/gl4es/angle/zink/dxvk)
 * @return SDL_TRUE 成功
 */
SDL_bool RAL_LoadRenderer(const char *renderer_name);

/**
 * 设置 GL 函数指针到 video device
 */
SDL_bool RAL_SetupGLFunctions(SDL_VideoDevice *device);

/**
 * 获取当前渲染器名称
 */
const char* RAL_GetCurrentRenderer(void);

/**
 * 获取当前渲染器的 EGL 库路径
 */
const char* RAL_GetCurrentRendererLibPath(void);

/* 向后兼容 */
#define Android_LoadRenderer RAL_LoadRenderer
#define Android_SetupGLFunctions RAL_SetupGLFunctions
#define Android_GetCurrentRenderer RAL_GetCurrentRenderer
#define Android_GetCurrentRendererLibPath RAL_GetCurrentRendererLibPath

#ifdef __cplusplus
}
#endif

#endif /* RAL_ANDROID_RENDERER_H */
