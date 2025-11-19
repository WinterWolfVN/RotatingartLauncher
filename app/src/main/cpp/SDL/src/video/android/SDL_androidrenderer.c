/*
  Simple DirectMedia Layer
  Copyright (C) 1997-2024 Sam Lantinga <slouken@libsdl.org>
*/

/**
 * SDL Android Dynamic Renderer Loader
 *
 * 基于环境变量的渲染器选择机制：
 * 1. 读取 RALCORE_RENDERER 环境变量确定渲染器类型
 * 2. 读取 RALCORE_EGL 环境变量（用于 ANGLE 等特殊渲染器）
 * 3. 使用 dlopen(RTLD_GLOBAL) 加载对应的渲染器库
 * 4. 通过 LD_PRELOAD 让系统自动使用加载的渲染器
 */

#include "../../SDL_internal.h"

#if SDL_VIDEO_DRIVER_ANDROID

#include "SDL_androidrenderer.h"
#include "SDL_androidgl.h"
#include "../SDL_egl_c.h"

#include <dlfcn.h>
#include <stdlib.h>
#include <android/log.h>

#define LOG_TAG "SDL_Renderer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* 渲染器后端配置表 */
static const SDL_RendererBackend RENDERER_BACKENDS[] = {
    /* 系统原生 EGL/GLES (默认) */
    {
        .name = "native",
        .egl_library = NULL,
        .gles_library = NULL,
        .need_preload = SDL_FALSE
    },

    /* gl4es (OpenGL 2.1 翻译到 GLES 2.0) */
    {
        .name = "gl4es",
        .egl_library = "libgl4es.so",
        .gles_library = "libgl4es.so",
        .need_preload = SDL_TRUE
    },

    /* ANGLE (OpenGL ES over Vulkan) */
    {
        .name = "angle",
        .egl_library = "libEGL_angle.so",
        .gles_library = "libGLESv2_angle.so",
        .need_preload = SDL_TRUE
    },

    /* Zink (OpenGL over Vulkan via OSMesa) */
    {
        .name = "zink",
        .egl_library = "libOSMesa.so",
        .gles_library = "libOSMesa.so",
        .need_preload = SDL_TRUE
    },

    /* 结束标记 */
    {NULL, NULL, NULL, SDL_FALSE}
};

/* 当前加载的渲染器 */
static const SDL_RendererBackend *current_renderer = NULL;
static void *renderer_handle = NULL;  /* dlopen 返回的句柄 */

/**
 * 查找渲染器配置
 */
static const SDL_RendererBackend* FindRendererBackend(const char *name)
{
    if (!name || name[0] == '\0') {
        return &RENDERER_BACKENDS[0];  /* 默认 native */
    }

    for (int i = 0; RENDERER_BACKENDS[i].name != NULL; i++) {
        if (SDL_strcasecmp(RENDERER_BACKENDS[i].name, name) == 0) {
            return &RENDERER_BACKENDS[i];
        }
    }

    LOGE("Unknown renderer '%s', falling back to native", name);
    return &RENDERER_BACKENDS[0];
}

/**
 * 从环境变量获取渲染器名称
 */
static const char* GetRendererFromEnv(void)
{
    const char *ralcore_renderer = SDL_getenv("RALCORE_RENDERER");
    const char *ralcore_egl = SDL_getenv("RALCORE_EGL");

    if (ralcore_renderer != NULL) {
        /* 映射 RALCORE_RENDERER 值到渲染器名称 */
        if (SDL_strcmp(ralcore_renderer, "opengles2") == 0) {
            return "gl4es";
        } else if (SDL_strcmp(ralcore_renderer, "vulkan_zink") == 0) {
            return "zink";
        } else if (SDL_strcmp(ralcore_renderer, "gallium_virgl") == 0) {
            return "virgl";
        } else if (SDL_strcmp(ralcore_renderer, "gallium_freedreno") == 0) {
            return "freedreno";
        }
    }

    if (ralcore_egl != NULL && SDL_strstr(ralcore_egl, "angle") != NULL) {
        return "angle";
    }

    return NULL; /* 使用默认 native 渲染器 */
}

/**
 * 加载渲染器库
 */
SDL_bool Android_LoadRenderer(const char *renderer_name)
{
    const SDL_RendererBackend *backend;
    const char *env_renderer;

    LOGI("================================================================");
    LOGI("  SDL Dynamic Renderer Loading");
    LOGI("  Requested: %s", renderer_name ? renderer_name : "(null)");

    /* 优先使用环境变量 */
    env_renderer = GetRendererFromEnv();
    if (env_renderer != NULL) {
        LOGI("  Environment: RALCORE_RENDERER/RALCORE_EGL -> %s", env_renderer);
        renderer_name = env_renderer;
    }

    LOGI("================================================================");

    /* 查找渲染器配置 */
    backend = FindRendererBackend(renderer_name);
    if (!backend) {
        LOGE("Failed to find renderer backend");
        return SDL_FALSE;
    }

    LOGI("  Selected: %s", backend->name);

    /* 如果是系统原生渲染器，无需加载 */
    if (!backend->need_preload) {
        LOGI("  Using system libEGL.so and libGLESv2.so");
        current_renderer = backend;
        return SDL_TRUE;
    }

    /* 检查库文件是否存在 */
    if (!backend->egl_library) {
        LOGE("  Renderer %s has no EGL library specified", backend->name);
        return SDL_FALSE;
    }

    LOGI("  EGL Library: %s", backend->egl_library);
    if (backend->gles_library && SDL_strcmp(backend->egl_library, backend->gles_library) != 0) {
        LOGI("  GLES Library: %s", backend->gles_library);
    }

    /* 使用 dlopen 加载渲染器库 (RTLD_GLOBAL 很关键!) */
    LOGI("  Loading with dlopen(RTLD_NOW | RTLD_GLOBAL)...");

    renderer_handle = dlopen(backend->egl_library, RTLD_NOW | RTLD_GLOBAL);
    if (!renderer_handle) {
        LOGE("  ✗ dlopen failed: %s", dlerror());
        LOGE("  Falling back to native renderer");
        current_renderer = &RENDERER_BACKENDS[0];
        return SDL_FALSE;
    }

    LOGI("  ✓ dlopen success, handle = %p", renderer_handle);

    /* 设置 LD_PRELOAD 环境变量 */
    /* 注意：setenv 必须在 SDL 初始化之前调用才有效 */
    /* 如果 SDL 已经初始化，需要在 Java 层设置 */
    if (setenv("LD_PRELOAD", backend->egl_library, 1) == 0) {
        LOGI("  ✓ LD_PRELOAD = %s", backend->egl_library);
    } else {
        LOGI("  ⚠ LD_PRELOAD already set or cannot be set");
    }

    /* 对于 gl4es，设置额外的环境变量 */
    if (SDL_strcasecmp(backend->name, "gl4es") == 0) {
        setenv("LIBGL_ES", "2", 1);         /* 使用 GLES 2.0 */
        setenv("LIBGL_MIPMAP", "3", 1);     /* 启用 mipmap */
        setenv("LIBGL_NPOT", "1", 1);       /* 支持非 2 的幂次纹理 */
        setenv("LIBGL_SHRINKPOP", "0", 1);  /* 禁用纹理缩小 */
        LOGI("  ✓ gl4es environment configured");
    }

    current_renderer = backend;
    LOGI("✅ Renderer '%s' loaded successfully", backend->name);
    LOGI("================================================================");

    return SDL_TRUE;
}

/**
 * 设置 GL 函数指针
 *
 * 由于使用 LD_PRELOAD，所有渲染器都提供标准 EGL 接口
 * 因此直接使用 SDL 的 EGL 函数即可
 */
SDL_bool Android_SetupGLFunctions(SDL_VideoDevice *device)
{
    const char *renderer_name;

    if (!device) {
        return SDL_FALSE;
    }

    renderer_name = current_renderer ? current_renderer->name : "native";
    LOGI("Setting up GL functions for renderer: %s", renderer_name);

    /* 所有渲染器都使用标准 EGL 接口 */
    device->GL_LoadLibrary = Android_GLES_LoadLibrary;
    device->GL_GetProcAddress = Android_GLES_GetProcAddress;
    device->GL_UnloadLibrary = Android_GLES_UnloadLibrary;
    device->GL_CreateContext = Android_GLES_CreateContext;
    device->GL_MakeCurrent = Android_GLES_MakeCurrent;
    device->GL_SetSwapInterval = Android_GLES_SetSwapInterval;
    device->GL_GetSwapInterval = Android_GLES_GetSwapInterval;
    device->GL_SwapWindow = Android_GLES_SwapWindow;
    device->GL_DeleteContext = Android_GLES_DeleteContext;

    LOGI("✓ GL functions configured");

    return SDL_TRUE;
}

/**
 * 获取当前渲染器名称
 */
const char* Android_GetCurrentRenderer(void)
{
    return current_renderer ? current_renderer->name : "none";
}

#endif /* SDL_VIDEO_DRIVER_ANDROID */
