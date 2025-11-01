/*
  Simple DirectMedia Layer
  Copyright (C) 1997-2024 Sam Lantinga <slouken@libsdl.org>

  This software is provided 'as-is', without any express or implied
  warranty.  In no event will the authors be held liable for any damages
  arising from the use of this software.

  Permission is granted to anyone to use this software for any purpose,
  including commercial applications, and to alter it and redistribute it
  freely, subject to the following restrictions:

  1. The origin of this software must not be misrepresented; you must not
     claim that you wrote the original software. If you use this software
     in a product, an acknowledgment in the product documentation would be
     appreciated but is not required.
  2. Altered source versions must be plainly marked as such, and must not be
     misrepresented as being the original software.
  3. This notice may not be removed or altered from any source distribution.
*/

#include "../../SDL_internal.h"

#if SDL_VIDEO_DRIVER_ANDROID && defined(SDL_VIDEO_OPENGL) && defined(SDL_VIDEO_OPENGL_GL4ES)

#include "SDL_androidvideo.h"
#include "SDL_androidwindow.h"
#include "SDL_androidgl.h"
#include "../SDL_sysvideo.h"

#include <android/log.h>
#include <stdlib.h>

#define LOG_TAG "SDL_GL4ES"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* ⚠️ 直接声明 gl4es AGL 函数（编译时链接，静态库符号）
 * gl4es 已静态链接到 libmain.so，这些符号在链接时可见
 * 无需使用 dlsym 动态查找，直接使用 extern 声明
 */
struct TagItem;  /* Forward declaration */
extern void* aglCreateContext2(unsigned long* errorCode, struct TagItem* tags);
extern void aglDestroyContext(void* context);
extern void aglMakeCurrent(void* context);
extern void aglSwapBuffers(void);
extern void* aglGetProcAddress(const char* proc);
extern int aglSetParams2(struct TagItem* tags);

/* gl4es configuration constants (similar to AmigaOS OGLES2_CCT_*) */
#define GL4ES_CCT_WINDOW        1
#define GL4ES_CCT_DEPTH         2
#define GL4ES_CCT_STENCIL       3
#define GL4ES_CCT_VSYNC         4
#define GL4ES_CCT_RESIZE_VIEWPORT 5

struct TagItem {
    unsigned int ti_Tag;
    unsigned long ti_Data;
};

#define TAG_DONE 0

/* 全局变量：存储当前AGL上下文和窗口
 * SDL_WindowData在禁用EGL后没有egl_context/egl_surface字段
 * 我们用全局变量管理AGL上下文（Android只有一个窗口）
 */
static void* g_agl_current_context = NULL;
static SDL_Window* g_agl_current_window = NULL;

/* gl4es 函数已通过 extern 声明直接链接，无需运行时初始化 */

int
Android_GL4ES_LoadLibrary(_THIS, const char* path)
{
    LOGI("🔵 Android_GL4ES_LoadLibrary called - gl4es functions linked directly");
    LOGI("   path=%s, _this=%p", path ? path : "(null)", _this);
    
    /* gl4es 已静态链接，AGL 函数通过 extern 声明直接可用
     * 不需要分配egl_data，因为我们不使用SDL的EGL代码路径
     * 所有OpenGL操作通过我们的Android_GL4ES_*函数完成
     */
    
    LOGI("✅ Android_GL4ES_LoadLibrary returning 0 (success)");
    return 0;
}

void*
Android_GL4ES_GetProcAddress(_THIS, const char* proc)
{
    LOGI("🔍 GetProcAddress: %s", proc ? proc : "(null)");
    void* func = aglGetProcAddress(proc);

    if (func == NULL) {
        LOGE("   ❌ Failed to load function '%s'", proc);
        SDL_SetError("Failed to load GL function");
    } else {
        LOGI("   ✅ Loaded '%s' at %p", proc, func);
    }

    return func;
}

void
Android_GL4ES_UnloadLibrary(_THIS)
{
    LOGI("Android_GL4ES_UnloadLibrary called");
    LOGI("gl4es library unload managed internally");
    /* gl4es handles cleanup internally */
}

SDL_GLContext
Android_GL4ES_CreateContext(_THIS, SDL_Window* window)
{
    LOGI("🎯 Android_GL4ES_CreateContext called for window '%s'", window ? window->title : "NULL");

    SDL_WindowData* data = (SDL_WindowData*)window->driverdata;
    if (!data) {
        LOGE("Window has no driver data");
        SDL_SetError("Window has no driver data");
        return NULL;
    }

    /* Delete old context if exists */
    if (g_agl_current_context != NULL) {
        LOGI("Old context found, deleting");
        aglDestroyContext(g_agl_current_context);
        g_agl_current_context = NULL;
        g_agl_current_window = NULL;
    }

    LOGI("Creating gl4es context with depth=%d, stencil=%d, native_window=%p",
         _this->gl_config.depth_size, _this->gl_config.stencil_size, data->native_window);

    /* Create gl4es context with configuration */
    struct TagItem create_context_tags[] = {
        {GL4ES_CCT_WINDOW, (unsigned long)data->native_window},
        {GL4ES_CCT_DEPTH, _this->gl_config.depth_size},
        {GL4ES_CCT_STENCIL, _this->gl_config.stencil_size},
        {GL4ES_CCT_VSYNC, 0},
        {GL4ES_CCT_RESIZE_VIEWPORT, 1},
        {TAG_DONE, 0}
    };

    unsigned long errCode = 0;
    void* context = aglCreateContext2(&errCode, create_context_tags);

    if (context) {
        LOGI("gl4es context %p created successfully", context);
        
        g_agl_current_context = context;
        g_agl_current_window = window;
        
        aglMakeCurrent(context);
        
        /* Clear buffers (important for depth buffer) */
        /* We need to call OpenGL functions through function pointers */
        typedef void (*glClear_func)(unsigned int mask);
        typedef void (*glViewport_func)(int x, int y, int width, int height);
        
        glClear_func glClear = (glClear_func)aglGetProcAddress("glClear");
        glViewport_func glViewport = (glViewport_func)aglGetProcAddress("glViewport");
        
        if (glClear && glViewport) {
            glClear(0x00004100); /* GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT */
            glViewport(0, 0, window->w, window->h);
        }
        
        return context;
    } else {
        LOGE("Failed to create gl4es context (error code: %lu)", errCode);
        SDL_SetError("Failed to create gl4es context");
        return NULL;
    }
}

int
Android_GL4ES_MakeCurrent(_THIS, SDL_Window* window, SDL_GLContext context)
{
    if (!window || !context) {
        LOGI("Android_GL4ES_MakeCurrent called with window=%p context=%p", window, context);
    }

    if (window && context) {
        if (context != g_agl_current_context) {
            LOGE("Context pointer mismatch: %p <> %p (global)", context, g_agl_current_context);
            SDL_SetError("Context pointer mismatch");
            return -1;
        }
        
        g_agl_current_window = window;
        aglMakeCurrent(context);
    }

    return 0;
}

int
Android_GL4ES_SwapWindow(_THIS, SDL_Window* window)
{
    if (g_agl_current_context != NULL) {
        /* Call glFinish before swap */
        typedef void (*glFinish_func)(void);
        glFinish_func glFinish = (glFinish_func)aglGetProcAddress("glFinish");
        if (glFinish) {
            glFinish();
        }

        aglSwapBuffers();
        return 0;
    } else {
        LOGE("No gl4es context");
        return -1;
    }
}

void
Android_GL4ES_DeleteContext(_THIS, SDL_GLContext context)
{
    LOGI("Android_GL4ES_DeleteContext called with context=%p", context);

    if (context) {
        if (g_agl_current_context == context) {
            LOGI("Destroying current gl4es context");
            aglDestroyContext(context);
            g_agl_current_context = NULL;
            g_agl_current_window = NULL;
        } else {
            LOGI("Context %p is not current (%p), just deleting", context, g_agl_current_context);
            aglDestroyContext(context);
        }
    } else {
        LOGI("No context to delete");
    }
}

void
Android_GL4ES_GetDrawableSize(_THIS, SDL_Window* window, int* w, int* h)
{
    LOGI("📐 GetDrawableSize called for window '%s'", window ? window->title : "NULL");
    if (w) {
        *w = window->w;
        LOGI("   width=%d", *w);
    }
    if (h) {
        *h = window->h;
        LOGI("   height=%d", *h);
    }
}

int
Android_GL4ES_SetSwapInterval(_THIS, int interval)
{
    LOGI("Android_GL4ES_SetSwapInterval: %d", interval);
    
    /* gl4es handles vsync internally on Android */
    /* We can store the preference but actual implementation is in gl4es */
    
    return 0; /* Success */
}

int
Android_GL4ES_GetSwapInterval(_THIS)
{
    /* Return default value since gl4es manages this internally */
    return 1;
}

#endif /* SDL_VIDEO_DRIVER_ANDROID && SDL_VIDEO_OPENGL && SDL_VIDEO_OPENGL_GL4ES */

