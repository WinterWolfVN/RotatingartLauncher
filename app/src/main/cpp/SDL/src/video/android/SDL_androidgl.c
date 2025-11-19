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

#if defined(SDL_VIDEO_DRIVER_ANDROID) && defined(SDL_VIDEO_OPENGL_EGL)

/* Android SDL video driver implementation */

#include "SDL_video.h"
#include "../SDL_egl_c.h"
#include "SDL_androidwindow.h"

#include "SDL_androidvideo.h"
#include "SDL_androidgl.h"
#include "../../core/android/SDL_android.h"

#include <android/log.h>

#include <dlfcn.h>

int Android_GLES_MakeCurrent(_THIS, SDL_Window *window, SDL_GLContext context)
{
    if (window && context) {
        return SDL_EGL_MakeCurrent(_this, ((SDL_WindowData *)window->driverdata)->egl_surface, context);
    } else {
        return SDL_EGL_MakeCurrent(_this, NULL, NULL);
    }
}

SDL_GLContext Android_GLES_CreateContext(_THIS, SDL_Window *window)
{
    SDL_GLContext ret;

    Android_ActivityMutex_Lock_Running();

    ret = SDL_EGL_CreateContext(_this, ((SDL_WindowData *)window->driverdata)->egl_surface);

    SDL_UnlockMutex(Android_ActivityMutex);

    return ret;
}

int Android_GLES_SwapWindow(_THIS, SDL_Window *window)
{
    int retval;

    SDL_LockMutex(Android_ActivityMutex);

    /* The following two calls existed in the original Java code
     * If you happen to have a device that's affected by their removal,
     * please report to our bug tracker. -- Gabriel
     */

    /*_this->egl_data->eglWaitNative(EGL_CORE_NATIVE_ENGINE);
    _this->egl_data->eglWaitGL();*/
    retval = SDL_EGL_SwapBuffers(_this, ((SDL_WindowData *)window->driverdata)->egl_surface);

    SDL_UnlockMutex(Android_ActivityMutex);

    return retval;
}
int Android_GLES_LoadLibrary(_THIS, const char *path)
{
    const char* custom_egl_path = NULL;

    /* 检查是否通过 FNA3D_OPENGL_LIBRARY 环境变量指定了自定义 EGL 库
     * 这个方法参考了 PojavLauncher 的实现,使用环境变量指定库路径绕过 Android 链接器命名空间限制
     * 参考: PojavLauncher egl_loader.c 的 POJAVEXEC_EGL 实现
     */
    custom_egl_path = SDL_getenv("FNA3D_OPENGL_LIBRARY");

    if (custom_egl_path != NULL && custom_egl_path[0] != '\0') {
        SDL_LogInfo(SDL_LOG_CATEGORY_VIDEO,
                    "Android_GLES_LoadLibrary: Using custom EGL from FNA3D_OPENGL_LIBRARY: %s",
                    custom_egl_path);
        return SDL_EGL_LoadLibrary(_this, custom_egl_path, (NativeDisplayType)0, 0);
    }

    /* 回退到默认行为(使用系统 libEGL.so) */
    return SDL_EGL_LoadLibrary(_this, path, (NativeDisplayType)0, 0);
}

void *Android_GLES_GetProcAddress(_THIS, const char *proc)
{
    return SDL_EGL_GetProcAddress(_this, proc);
}

void Android_GLES_UnloadLibrary(_THIS)
{
    SDL_EGL_UnloadLibrary(_this);
}

int Android_GLES_SetSwapInterval(_THIS, int interval)
{
    return SDL_EGL_SetSwapInterval(_this, interval);
}

int Android_GLES_GetSwapInterval(_THIS)
{
    return SDL_EGL_GetSwapInterval(_this);
}

void Android_GLES_DeleteContext(_THIS, SDL_GLContext context)
{
    SDL_EGL_DeleteContext(_this, context);
}

#endif /* SDL_VIDEO_DRIVER_ANDROID */

/* vi: set ts=4 sw=4 expandtab: */
