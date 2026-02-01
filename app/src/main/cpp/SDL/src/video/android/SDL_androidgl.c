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

#include <unistd.h> // for usleep

/* Android SDL video driver implementation */

#include "SDL_video.h"
#include "../SDL_egl_c.h"
#include "SDL_androidwindow.h"

#include "SDL_androidvideo.h"
#include "SDL_androidgl.h"
#include "../../core/android/SDL_android.h"

#include <android/log.h>
#include <android/native_window.h>

#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <time.h>
#include "../../SDL_internal.h"  // SDL_bool is defined here
#include "SDL_androidwindow.h"  // For SDL_WindowData

/* FPS 计算相关 - 使用滑动窗口平均实现精准稳定的检测 */
#define FPS_SAMPLE_COUNT 60  /* 滑动窗口大小（帧数） */
#define FPS_UPDATE_INTERVAL_NS 250000000LL  /* 更新间隔 250ms（纳秒） */

static Uint64 g_frame_times[FPS_SAMPLE_COUNT] = {0};  /* 帧时间戳环形缓冲 */
static int g_frame_index = 0;                          /* 当前帧索引 */
static int g_frame_count = 0;                          /* 有效帧计数 */
static Uint64 g_last_update_ns = 0;                    /* 上次更新时间 */
static float g_fps_current = 0.0f;                     /* 当前 FPS */
static float g_fps_smoothed = 0.0f;                    /* 平滑后的 FPS */
static float g_frame_time_ms = 0.0f;                   /* 平均帧时间 */
static char g_fps_env_buffer[64] = {0};
static SDL_bool g_fps_initialized = SDL_FALSE;

/* 获取当前时间（纳秒级精度） */
static Uint64 GetTimeNS(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (Uint64)ts.tv_sec * 1000000000ULL + (Uint64)ts.tv_nsec;
}

/* 初始化 FPS 追踪 */
static void InitFPSTracking(void)
{
    if (g_fps_initialized) return;
    g_fps_initialized = SDL_TRUE;
    
    SDL_memset(g_frame_times, 0, sizeof(g_frame_times));
    g_frame_index = 0;
    g_frame_count = 0;
    g_last_update_ns = GetTimeNS();
    g_fps_current = 0.0f;
    g_fps_smoothed = 0.0f;
    
    setenv("RAL_FPS", "0", 1);
    setenv("RAL_FRAME_TIME", "0", 1);
    
    __android_log_print(ANDROID_LOG_INFO, "SDL_FPS", 
        "FPS tracking initialized (sample window: %d frames)", FPS_SAMPLE_COUNT);
}

/* 更新 FPS - 使用滑动窗口平均 */
static void UpdateFPS(void)
{
    Uint64 current_ns = GetTimeNS();
    
    /* 记录帧时间到环形缓冲 */
    g_frame_times[g_frame_index] = current_ns;
    g_frame_index = (g_frame_index + 1) % FPS_SAMPLE_COUNT;
    if (g_frame_count < FPS_SAMPLE_COUNT) {
        g_frame_count++;
    }
    
    /* 检查是否需要更新 FPS 显示 */
    Uint64 elapsed_ns = current_ns - g_last_update_ns;
    if (elapsed_ns < FPS_UPDATE_INTERVAL_NS) {
        return;
    }
    g_last_update_ns = current_ns;
    
    /* 计算滑动窗口内的平均 FPS */
    if (g_frame_count >= 2) {
        /* 找到窗口内最老的帧 */
        int oldest_index = (g_frame_index - g_frame_count + FPS_SAMPLE_COUNT) % FPS_SAMPLE_COUNT;
        int newest_index = (g_frame_index - 1 + FPS_SAMPLE_COUNT) % FPS_SAMPLE_COUNT;
        
        Uint64 oldest_time = g_frame_times[oldest_index];
        Uint64 newest_time = g_frame_times[newest_index];
        
        if (newest_time > oldest_time) {
            Uint64 window_duration_ns = newest_time - oldest_time;
            int frame_intervals = g_frame_count - 1;
            
            /* 计算 FPS: (帧数-1) / 时间窗口 */
            g_fps_current = (float)frame_intervals * 1000000000.0f / (float)window_duration_ns;
            
            /* 计算平均帧时间 (ms) */
            g_frame_time_ms = (float)window_duration_ns / (float)frame_intervals / 1000000.0f;
            
            /* 指数移动平均平滑 (EMA)，alpha = 0.3 */
            if (g_fps_smoothed <= 0.0f) {
                g_fps_smoothed = g_fps_current;
            } else {
                g_fps_smoothed = g_fps_smoothed * 0.7f + g_fps_current * 0.3f;
            }
        }
    }
    
    /* 通过环境变量传递给 Java */
    SDL_snprintf(g_fps_env_buffer, sizeof(g_fps_env_buffer), "%.1f", g_fps_smoothed);
    setenv("RAL_FPS", g_fps_env_buffer, 1);
    
    SDL_snprintf(g_fps_env_buffer, sizeof(g_fps_env_buffer), "%.2f", g_frame_time_ms);
    setenv("RAL_FRAME_TIME", g_fps_env_buffer, 1);
}

int Android_GLES_MakeCurrent(_THIS, SDL_Window *window, SDL_GLContext context)
{
    if (window && context) {
        /* For OSMesa zink, create OSMesa context when SDL context is made current
         * This ensures OSMesa context is ready before FNA3D calls glGetString()
         */
#ifdef __ANDROID__
        const char *fna3d_gl_lib = SDL_getenv("FNA3D_OPENGL_LIBRARY");
        SDL_bool is_osmesa = (fna3d_gl_lib && SDL_strcasestr(fna3d_gl_lib, "osmesa"));

        __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
            "MakeCurrent: FNA3D_OPENGL_LIBRARY=%s, is_osmesa=%d",
            fna3d_gl_lib ? fna3d_gl_lib : "(null)", is_osmesa);

        if (is_osmesa) {
            /* Try to load osm_renderer_init from the main library using dlopen/dlsym
             * Try multiple possible library names
             */
            static bool osm_initialized = false;
            if (!osm_initialized) {
                const char* lib_names[] = {"libralaunch.so", "libmain.so", NULL};
                void *main_lib = NULL;
                int i = 0;

                while (lib_names[i] != NULL && main_lib == NULL) {
                    main_lib = dlopen(lib_names[i], RTLD_LAZY | RTLD_LOCAL);
                    if (main_lib) {
                        __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
                            "✓ Loaded library: %s", lib_names[i]);
                    } else {
                        __android_log_print(ANDROID_LOG_WARN, "Android_GLES",
                            "⚠ Failed to load %s: %s", lib_names[i], dlerror());
                    }
                    i++;
                }

                if (main_lib) {
                    typedef bool (*osm_renderer_init_func)(ANativeWindow*);
                    typedef bool (*osm_renderer_is_available_func)(void);
                    typedef bool (*osm_renderer_is_initialized_func)(void);

                    osm_renderer_is_available_func osm_is_available = (osm_renderer_is_available_func)
                        dlsym(main_lib, "osm_renderer_is_available");
                    osm_renderer_is_initialized_func osm_is_initialized = (osm_renderer_is_initialized_func)
                        dlsym(main_lib, "osm_renderer_is_initialized");
                    osm_renderer_init_func osm_init = (osm_renderer_init_func)
                        dlsym(main_lib, "osm_renderer_init");

                    __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
                        "OSMesa function pointers: is_available=%p, is_initialized=%p, init=%p",
                        osm_is_available, osm_is_initialized, osm_init);

                    if (osm_is_available && osm_is_initialized && osm_init) {
                        __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
                            "All OSMesa functions loaded successfully");

                        if (osm_is_available() && !osm_is_initialized()) {
                            SDL_WindowData *data = (SDL_WindowData *)window->driverdata;
                            ANativeWindow *native_window = data->native_window;

                            if (native_window != NULL) {
                                __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
                                    "Creating OSMesa context when SDL context is made current...");
                                if (osm_init(native_window)) {
                                    __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
                                        "✓ OSMesa context created and made current");

                                    // CRITICAL: Wait a bit for OSMesa context to be fully ready
                                    // This ensures glGetString() will work when FNA3D initializes
                                    // zink needs time to initialize the Vulkan device and OpenGL context
                                    __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
                                        "Waiting for OSMesa context to be fully ready...");
                                    usleep(150000); // 150ms delay for zink initialization

                                    osm_initialized = true;
                                } else {
                                    __android_log_print(ANDROID_LOG_WARN, "Android_GLES",
                                        "⚠ Failed to create OSMesa context, will use EGL fallback");
                                }
                            } else {
                                __android_log_print(ANDROID_LOG_WARN, "Android_GLES",
                                    "⚠ Native window is NULL, cannot create OSMesa context");
                            }
                        } else if (osm_is_initialized()) {
                            osm_initialized = true;
                        }
                    } else {
                        __android_log_print(ANDROID_LOG_WARN, "Android_GLES",
                            "⚠ Failed to load OSMesa functions from library");
                    }
                    // Don't close main_lib, we need the symbols to remain available
                } else {
                    __android_log_print(ANDROID_LOG_WARN, "Android_GLES",
                        "⚠ Failed to load any OSMesa-compatible library");
                }
            }

            /* For OSMesa, don't call SDL_EGL_MakeCurrent - OSMesa manages its own context */
            __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
                "OSMesa mode: returning success without EGL MakeCurrent");
            return 0;
        }
#endif
        return SDL_EGL_MakeCurrent(_this, ((SDL_WindowData *)window->driverdata)->egl_surface, context);
    } else {
        return SDL_EGL_MakeCurrent(_this, NULL, NULL);
    }
}

SDL_GLContext Android_GLES_CreateContext(_THIS, SDL_Window *window)
{
    SDL_GLContext ret;

    Android_ActivityMutex_Lock_Running();

#ifdef __ANDROID__
    /* For OSMesa, return a dummy context since OSMesa manages its own OpenGL context */
    const char *fna3d_gl_lib = SDL_getenv("FNA3D_OPENGL_LIBRARY");
    SDL_bool is_osmesa = (fna3d_gl_lib && SDL_strcasestr(fna3d_gl_lib, "osmesa"));

    if (is_osmesa) {
        __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
            "OSMesa detected, returning dummy GL context (OSMesa manages its own context)");
        /* Return a non-NULL dummy pointer to indicate success
         * OSMesa context is managed separately via osm_init_context()
         * We use (void*)1 as a sentinel value to indicate "OSMesa mode"
         */
        SDL_UnlockMutex(Android_ActivityMutex);
        return (SDL_GLContext)(void*)1;
    }
#endif

    ret = SDL_EGL_CreateContext(_this, ((SDL_WindowData *)window->driverdata)->egl_surface);

    SDL_UnlockMutex(Android_ActivityMutex);

    return ret;
}

int Android_GLES_SwapWindow(_THIS, SDL_Window *window)
{
    int retval;

    /* 初始化并更新 FPS（滑动窗口平均算法） */
    InitFPSTracking();
    UpdateFPS();

    SDL_LockMutex(Android_ActivityMutex);

#ifdef __ANDROID__
    /* For OSMesa/zink rendering, use OSMesa swap buffers instead of EGL
     * OSMesa renders to a software buffer that needs to be copied to the native window
     */
    const char *fna3d_gl_lib = SDL_getenv("FNA3D_OPENGL_LIBRARY");
    SDL_bool is_osmesa = (fna3d_gl_lib && SDL_strcasestr(fna3d_gl_lib, "osmesa"));

    if (is_osmesa) {
        /* Try to call osm_swap_buffers from the main library */
        static void (*osm_swap_buffers_fn)(void) = NULL;
        static SDL_bool osm_swap_init_attempted = SDL_FALSE;

        if (!osm_swap_init_attempted) {
            osm_swap_init_attempted = SDL_TRUE;
            void* main_lib = dlopen("libmain.so", RTLD_LAZY | RTLD_LOCAL);
            if (main_lib) {
                osm_swap_buffers_fn = (void (*)(void)) dlsym(main_lib, "osm_swap_buffers");
                if (osm_swap_buffers_fn) {
                    __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
                        "✓ Found osm_swap_buffers function for OSMesa rendering");
                }
            }
        }

        if (osm_swap_buffers_fn) {
            osm_swap_buffers_fn();
            SDL_UnlockMutex(Android_ActivityMutex);
            return 0;
        } else {
            __android_log_print(ANDROID_LOG_WARN, "Android_GLES",
                "⚠ osm_swap_buffers not found, falling back to EGL swap");
        }
    }
#endif

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
    const char* current_renderer = NULL;
    const char* egl_lib_path = NULL;

    __android_log_print(ANDROID_LOG_INFO, "Android_GLES", "Android_GLES_LoadLibrary called, path=%s", path ? path : "(null)");

    /* 检查是否已经通过 Android_LoadRenderer() 预加载了渲染器
     * 如果已预加载，需要传递库路径让 SDL_EGL_LoadLibrary 使用该库
     */
    #ifdef SDL_VIDEO_DRIVER_ANDROID
    extern const char* Android_GetCurrentRenderer(void);
    extern const char* Android_GetCurrentRendererLibPath(void);

    current_renderer = Android_GetCurrentRenderer();
    egl_lib_path = Android_GetCurrentRendererLibPath();

    __android_log_print(ANDROID_LOG_INFO, "Android_GLES", "current_renderer = %s, egl_lib_path = %s",
                        current_renderer ? current_renderer : "(null)",
                        egl_lib_path ? egl_lib_path : "(null)");

    if (current_renderer && SDL_strcmp(current_renderer, "native") != 0 && SDL_strcmp(current_renderer, "none") != 0) {
        __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
                    "Renderer '%s' already preloaded", current_renderer);

        /* 检查是否是 OSMesa 渲染器（zink/virgl 等）
         * OSMesa 渲染器需要使用 OSMesa 库路径
         * 其他渲染器（gl4es 等）使用系统 EGL + 自定义 GL 库
         */
        const char* fna3d_ogl_lib = SDL_getenv("FNA3D_OPENGL_LIBRARY");
        SDL_bool is_osmesa = (fna3d_ogl_lib && SDL_strcasestr(fna3d_ogl_lib, "osmesa"));

        // 检查是否是 zink 渲染器
        const char *fna3d_driver = SDL_getenv("FNA3D_OPENGL_DRIVER");
        SDL_bool is_zink = (current_renderer && (SDL_strcmp(current_renderer, "zink") == 0 ||
                                                 SDL_strstr(current_renderer, "vulkan_zink") != NULL)) ||
                           (fna3d_driver && SDL_strcasecmp(fna3d_driver, "zink") == 0);

        if (is_zink) {
            __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
                        "Zink renderer detected, checking Vulkan availability...");

            /* 检查 VULKAN_PTR 环境变量（由 Java 层设置） */
            const char* vulkan_ptr = SDL_getenv("VULKAN_PTR");
            if (vulkan_ptr != NULL && vulkan_ptr[0] != '\0') {
                __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
                            "✓ Vulkan library already loaded (VULKAN_PTR=%s)", vulkan_ptr);
            } else {
                /* 如果 Java 层没有加载，尝试在这里加载 */
                __android_log_print(ANDROID_LOG_WARN, "Android_GLES",
                            "⚠ VULKAN_PTR not set, attempting to load Vulkan...");
                void* vulkan_handle = dlopen("libvulkan.so", RTLD_LAZY | RTLD_LOCAL);
                if (vulkan_handle != NULL) {
                    char envval[64];
                    SDL_snprintf(envval, sizeof(envval), "%p", vulkan_handle);
                    SDL_setenv("VULKAN_PTR", envval, 1);
                    __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
                                "✓ Vulkan library loaded: %s", envval);
                } else {
                    __android_log_print(ANDROID_LOG_WARN, "Android_GLES",
                                "⚠ Failed to load Vulkan library: %s", dlerror());
                }
            }
        }

        /* 对于 OSMesa 渲染器，传递 OSMesa 库路径给 SDL_EGL_LoadLibrary
         * OSMesa 不使用真正的 EGL，但需要加载库以获取 GL 函数
         */
        if (is_osmesa && fna3d_ogl_lib) {
            __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
                        "OSMesa renderer: Using OSMesa library: %s", fna3d_ogl_lib);
            return SDL_EGL_LoadLibrary(_this, fna3d_ogl_lib, (NativeDisplayType)0, 0);
        }

        /* 对于非 OSMesa 渲染器（gl4es 等），使用系统 EGL
         * GL 函数将通过 eglGetProcAddress 或 dlsym 从预加载的 GL 库获取
         */
        __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
                    "Non-OSMesa renderer '%s': Using system EGL with preloaded GL library",
                    current_renderer);
        return SDL_EGL_LoadLibrary(_this, egl_lib_path, (NativeDisplayType)0, 0);
    }
    #endif

    /* 检查是否通过 FNA3D_OPENGL_LIBRARY 环境变量指定了自定义 EGL 库
     * 使用环境变量指定库路径绕过 Android 链接器命名空间限制
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
#ifdef __ANDROID__
    const char *fna3d_gl_lib = SDL_getenv("FNA3D_OPENGL_LIBRARY");
    SDL_bool is_osmesa = (fna3d_gl_lib && SDL_strcasestr(fna3d_gl_lib, "osmesa"));

    /* For OSMesa, use OSMesaGetProcAddress */
    if (is_osmesa) {
        static void* (*OSMesaGetProcAddress_fn)(const char*) = NULL;
        static SDL_bool osmesa_proc_init_attempted = SDL_FALSE;
        static int log_count = 0;

        if (!osmesa_proc_init_attempted) {
            osmesa_proc_init_attempted = SDL_TRUE;
            void* osmesa_lib = dlopen(fna3d_gl_lib, RTLD_LAZY | RTLD_LOCAL);
            if (osmesa_lib) {
                OSMesaGetProcAddress_fn = (void* (*)(const char*)) dlsym(osmesa_lib, "OSMesaGetProcAddress");
                if (OSMesaGetProcAddress_fn) {
                    __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
                        "✓ OSMesaGetProcAddress loaded for GL function lookup from %s", fna3d_gl_lib);
                } else {
                    __android_log_print(ANDROID_LOG_ERROR, "Android_GLES",
                        "✗ OSMesaGetProcAddress NOT found in %s", fna3d_gl_lib);
                }
            } else {
                __android_log_print(ANDROID_LOG_ERROR, "Android_GLES",
                    "✗ Failed to dlopen OSMesa library: %s - %s", fna3d_gl_lib, dlerror());
            }
        }

        if (OSMesaGetProcAddress_fn) {
            void* result = OSMesaGetProcAddress_fn(proc);
            if (log_count < 20) {
                __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
                    "GetProcAddress(%s) -> OSMesa: %p", proc, result);
                log_count++;
            }
            if (result) {
                return result;
            }
            __android_log_print(ANDROID_LOG_WARN, "Android_GLES",
                "OSMesaGetProcAddress returned NULL for %s, falling back to EGL", proc);
        }
    }

    /* For custom GL libraries (gl4es, etc.), try dlsym first
     * These libraries provide their own GL implementations
     */
    if (fna3d_gl_lib && !is_osmesa) {
        static void* custom_gl_lib = NULL;
        static SDL_bool custom_gl_init_attempted = SDL_FALSE;
        static int custom_log_count = 0;

        if (!custom_gl_init_attempted) {
            custom_gl_init_attempted = SDL_TRUE;
            /* Open with RTLD_NOLOAD to get the already-loaded library handle */
            custom_gl_lib = dlopen(fna3d_gl_lib, RTLD_LAZY | RTLD_NOLOAD);
            if (!custom_gl_lib) {
                /* Try opening it normally */
                custom_gl_lib = dlopen(fna3d_gl_lib, RTLD_LAZY | RTLD_LOCAL);
            }
            if (custom_gl_lib) {
                __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
                    "✓ Custom GL library loaded for function lookup: %s", fna3d_gl_lib);
            } else {
                __android_log_print(ANDROID_LOG_WARN, "Android_GLES",
                    "⚠ Failed to load custom GL library: %s - %s", fna3d_gl_lib, dlerror());
            }
        }

        if (custom_gl_lib) {
            void* result = dlsym(custom_gl_lib, proc);
            if (result) {
                if (custom_log_count < 20) {
                    __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
                        "GetProcAddress(%s) -> Custom GL: %p", proc, result);
                    custom_log_count++;
                }
                return result;
            }
            /* Fall through to EGL if custom library doesn't have the function */
        }
    }
#endif
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
#ifdef __ANDROID__
    /* For OSMesa, the context is a dummy pointer - don't call EGL delete */
    const char *fna3d_gl_lib = SDL_getenv("FNA3D_OPENGL_LIBRARY");
    SDL_bool is_osmesa = (fna3d_gl_lib && SDL_strcasestr(fna3d_gl_lib, "osmesa"));

    if (is_osmesa) {
        __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
            "OSMesa mode: skipping EGL DeleteContext (OSMesa manages its own context)");
        return;
    }
#endif
    SDL_EGL_DeleteContext(_this, context);
}

void Android_GLES_GetDrawableSize(_THIS, SDL_Window *window, int *w, int *h)
{
#ifdef __ANDROID__
    const char *fna3d_gl_lib = SDL_getenv("FNA3D_OPENGL_LIBRARY");
    SDL_bool is_osmesa = (fna3d_gl_lib && SDL_strcasestr(fna3d_gl_lib, "osmesa"));

    if (is_osmesa) {
        /* For OSMesa, get the drawable size from the native window */
        SDL_WindowData *data = (SDL_WindowData *)window->driverdata;
        if (data && data->native_window) {
            int native_w = ANativeWindow_getWidth(data->native_window);
            int native_h = ANativeWindow_getHeight(data->native_window);
            if (native_w > 0 && native_h > 0) {
                if (w) *w = native_w;
                if (h) *h = native_h;
                __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
                    "OSMesa GetDrawableSize: %dx%d (from ANativeWindow)", native_w, native_h);
                return;
            }
        }
        /* Fall back to window size */
        if (w) *w = window->w;
        if (h) *h = window->h;
        __android_log_print(ANDROID_LOG_INFO, "Android_GLES",
            "OSMesa GetDrawableSize: %dx%d (fallback to window size)", window->w, window->h);
        return;
    }
#endif
    /* Default: use window size in pixels */
    SDL_GetWindowSizeInPixels(window, w, h);
}

#endif /* SDL_VIDEO_DRIVER_ANDROID */

/* vi: set ts=4 sw=4 expandtab: */
