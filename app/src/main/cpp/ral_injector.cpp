#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>

#define LOG_TAG "RAL_INJECTOR"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT void JNICALL
Java_com_app_ralaunch_core_platform_runtime_renderer_RendererBootstrap_nativeForceLoadLibs(
    JNIEnv* env, jobject /* this */, jstring eglPathStr, jstring glesPathStr) {

    const char* eglPath = eglPathStr ? env->GetStringUTFChars(eglPathStr, nullptr) : nullptr;
    const char* glesPath = glesPathStr ? env->GetStringUTFChars(glesPathStr, nullptr) : nullptr;
  
    dlopen("libc++_shared.so", RTLD_NOW | RTLD_GLOBAL);

    if (eglPath) {
        void* handleEgl = dlopen(eglPath, RTLD_NOW | RTLD_GLOBAL);
        if (handleEgl) {
            LOGI("Force loaded EGL: %s", eglPath);
        } else {
            LOGE("Failed to load EGL: %s, Error: %s", eglPath, dlerror());
        }
        env->ReleaseStringUTFChars(eglPathStr, eglPath);
    }

    if (glesPath && glesPath != eglPath) {
        void* handleGles = dlopen(glesPath, RTLD_NOW | RTLD_GLOBAL);
        if (handleGles) {
            LOGI("Force loaded GLES: %s", glesPath);
        } else {
            LOGE("Failed to load GLES: %s, Error: %s", glesPath, dlerror());
        }
        env->ReleaseStringUTFChars(glesPathStr, glesPath);
    }
}
