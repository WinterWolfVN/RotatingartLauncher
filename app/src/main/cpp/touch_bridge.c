/**
 * Touch Bridge - 触摸数据桥接
 * 在 Java 和 C# 之间共享触摸数据
 */

#include <jni.h>
#include <android/log.h>
#include <string.h>

#define TAG "TouchBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// 最大支持 10 个触摸点
#define MAX_TOUCHES 10
// 最大虚拟触屏点（来自虚拟按钮/摇杆）
#define MAX_VIRTUAL_TOUCHES 5

// 触摸数据结构
typedef struct {
    int count;
    float x[MAX_TOUCHES];
    float y[MAX_TOUCHES];
    int screen_width;
    int screen_height;
} TouchData;

// 虚拟触屏点数据（来自虚拟按钮/摇杆）
typedef struct {
    int count;
    float x[MAX_VIRTUAL_TOUCHES];
    float y[MAX_VIRTUAL_TOUCHES];
    int active[MAX_VIRTUAL_TOUCHES];  // 是否激活
} VirtualTouchData;

// 全局触摸数据
static TouchData g_touch_data = {0};
// 全局虚拟触屏数据
static VirtualTouchData g_virtual_touch = {0};

// 虚拟鼠标位置（用于右摇杆鼠标移动）
static float g_virtual_mouse_x = 0.0f;
static float g_virtual_mouse_y = 0.0f;
static int g_virtual_mouse_active = 0;  // 是否启用虚拟鼠标
// 虚拟鼠标移动范围（屏幕百分比 0.0-1.0）
static float g_mouse_range_left = 0.0f;
static float g_mouse_range_top = 0.0f;
static float g_mouse_range_right = 1.0f;
static float g_mouse_range_bottom = 1.0f;

// ===== 供 Java 调用的 JNI 函数 =====

// 通用的设置触摸数据函数
static void setTouchDataInternal(JNIEnv *env, int count, jfloatArray x_arr, jfloatArray y_arr, int screenWidth, int screenHeight) {
    g_touch_data.screen_width = screenWidth;
    g_touch_data.screen_height = screenHeight;
    
    if (count <= 0) {
        // 清除触摸数据
        g_touch_data.count = 0;
        return;
    }
    
    g_touch_data.count = count > MAX_TOUCHES ? MAX_TOUCHES : count;
    
    if (x_arr != NULL && y_arr != NULL) {
        jfloat* x_data = (*env)->GetFloatArrayElements(env, x_arr, NULL);
        jfloat* y_data = (*env)->GetFloatArrayElements(env, y_arr, NULL);
        
        for (int i = 0; i < g_touch_data.count; i++) {
            g_touch_data.x[i] = x_data[i];
            g_touch_data.y[i] = y_data[i];
        }
        
        (*env)->ReleaseFloatArrayElements(env, x_arr, x_data, 0);
        (*env)->ReleaseFloatArrayElements(env, y_arr, y_data, 0);
        
        // Debug log for multi-touch
        if (count > 1) {
            LOGI("Multi-touch: count=%d", count);
        }
    } else {
        // 如果数组为空，清除数据
        g_touch_data.count = 0;
    }
}

// SDLSurface JNI 函数
JNIEXPORT void JNICALL
Java_org_libsdl_app_SDLSurface_nativeSetTouchData(JNIEnv *env, jclass clazz,
    int count, jfloatArray x_arr, jfloatArray y_arr, int screenWidth, int screenHeight) {
    setTouchDataInternal(env, count, x_arr, y_arr, screenWidth, screenHeight);
}

JNIEXPORT void JNICALL
Java_org_libsdl_app_SDLSurface_nativeClearTouchData(JNIEnv *env, jclass clazz) {
    g_touch_data.count = 0;
}

// GameActivity JNI 函数
JNIEXPORT void JNICALL
Java_com_app_ralaunch_activity_GameActivity_nativeSetTouchData(JNIEnv *env, jclass clazz,
    int count, jfloatArray x_arr, jfloatArray y_arr, int screenWidth, int screenHeight) {
    setTouchDataInternal(env, count, x_arr, y_arr, screenWidth, screenHeight);
}

JNIEXPORT void JNICALL
Java_com_app_ralaunch_activity_GameActivity_nativeClearTouchData(JNIEnv *env, jclass clazz) {
    g_touch_data.count = 0;
}

// ===== 虚拟触屏 JNI 函数（供 Java 虚拟按钮/摇杆调用）=====

// 设置虚拟触屏点（按下）
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeSetVirtualTouch(JNIEnv *env, jclass clazz,
    int index, float x, float y, int screenWidth, int screenHeight) {
    if (index >= 0 && index < MAX_VIRTUAL_TOUCHES) {
        g_virtual_touch.x[index] = x / screenWidth;  // 存储为归一化坐标
        g_virtual_touch.y[index] = y / screenHeight;
        g_virtual_touch.active[index] = 1;

        // 更新屏幕尺寸
        if (g_touch_data.screen_width == 0) {
            g_touch_data.screen_width = screenWidth;
            g_touch_data.screen_height = screenHeight;
        }

        LOGI("Virtual touch set: index=%d, x=%.1f, y=%.1f", index, x, y);
    }
}

// 清除虚拟触屏点（释放）
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeClearVirtualTouch(JNIEnv *env, jclass clazz,
    int index) {
    if (index >= 0 && index < MAX_VIRTUAL_TOUCHES) {
        g_virtual_touch.active[index] = 0;
        LOGI("Virtual touch cleared: index=%d", index);
    }
}

// 清除所有虚拟触屏点
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeClearAllVirtualTouches(JNIEnv *env, jclass clazz) {
    for (int i = 0; i < MAX_VIRTUAL_TOUCHES; i++) {
        g_virtual_touch.active[i] = 0;
    }
    LOGI("All virtual touches cleared");
}

// ===== 虚拟鼠标位置 JNI 函数（供右摇杆鼠标移动使用）=====

// 启用虚拟鼠标并设置初始位置（屏幕中心）
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeEnableVirtualMouse(JNIEnv *env, jclass clazz,
    int screenWidth, int screenHeight) {
    g_virtual_mouse_active = 1;
    g_virtual_mouse_x = screenWidth / 2.0f;
    g_virtual_mouse_y = screenHeight / 2.0f;
    g_touch_data.screen_width = screenWidth;
    g_touch_data.screen_height = screenHeight;
    LOGI("Virtual mouse enabled at (%.1f, %.1f)", g_virtual_mouse_x, g_virtual_mouse_y);
}

// 禁用虚拟鼠标
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeDisableVirtualMouse(JNIEnv *env, jclass clazz) {
    g_virtual_mouse_active = 0;
    LOGI("Virtual mouse disabled");
}

// 设置虚拟鼠标移动范围
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeSetVirtualMouseRange(JNIEnv *env, jclass clazz,
    float left, float top, float right, float bottom) {
    g_mouse_range_left = left;
    g_mouse_range_top = top;
    g_mouse_range_right = right;
    g_mouse_range_bottom = bottom;
    LOGI("Virtual mouse range set: left=%.2f(%.0fpx), top=%.2f(%.0fpx), right=%.2f(%.0fpx), bottom=%.2f(%.0fpx)", 
        left, left * g_touch_data.screen_width,
        top, top * g_touch_data.screen_height,
        right, right * g_touch_data.screen_width,
        bottom, bottom * g_touch_data.screen_height);
}

// 更新虚拟鼠标位置（相对移动）
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeUpdateVirtualMouseDelta(JNIEnv *env, jclass clazz,
    float deltaX, float deltaY) {
    if (g_virtual_mouse_active && g_touch_data.screen_width > 0) {
        g_virtual_mouse_x += deltaX;
        g_virtual_mouse_y += deltaY;

        // 使用用户配置的范围值（百分比转像素）
        float minX = g_mouse_range_left * g_touch_data.screen_width;
        float maxX = g_mouse_range_right * g_touch_data.screen_width;
        float minY = g_mouse_range_top * g_touch_data.screen_height;
        float maxY = g_mouse_range_bottom * g_touch_data.screen_height;

        // 如果范围设置错误（min > max），交换它们
        if (minX > maxX) { float tmp = minX; minX = maxX; maxX = tmp; }
        if (minY > maxY) { float tmp = minY; minY = maxY; maxY = tmp; }
        
        // 如果范围太小（小于10%屏幕），使用全屏
        if ((maxX - minX) < g_touch_data.screen_width * 0.1f) {
            minX = 0;
            maxX = g_touch_data.screen_width;
        }
        if ((maxY - minY) < g_touch_data.screen_height * 0.1f) {
            minY = 0;
            maxY = g_touch_data.screen_height;
        }

        // 限制在范围内
        if (g_virtual_mouse_x < minX) g_virtual_mouse_x = minX;
        if (g_virtual_mouse_y < minY) g_virtual_mouse_y = minY;
        if (g_virtual_mouse_x > maxX) g_virtual_mouse_x = maxX;
        if (g_virtual_mouse_y > maxY) g_virtual_mouse_y = maxY;
    }
}

// 设置虚拟鼠标绝对位置
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeSetVirtualMousePosition(JNIEnv *env, jclass clazz,
    float x, float y) {
    if (g_virtual_mouse_active) {
        g_virtual_mouse_x = x;
        g_virtual_mouse_y = y;

        // 限制在屏幕范围内
        if (g_virtual_mouse_x < 0) g_virtual_mouse_x = 0;
        if (g_virtual_mouse_y < 0) g_virtual_mouse_y = 0;
        if (g_virtual_mouse_x > g_touch_data.screen_width) g_virtual_mouse_x = g_touch_data.screen_width;
        if (g_virtual_mouse_y > g_touch_data.screen_height) g_virtual_mouse_y = g_touch_data.screen_height;
    }
}

// 获取虚拟鼠标X位置（供Java调用）
JNIEXPORT float JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeGetVirtualMouseX(JNIEnv *env, jclass clazz) {
    return g_virtual_mouse_x;
}

// 获取虚拟鼠标Y位置（供Java调用）
JNIEXPORT float JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeGetVirtualMouseY(JNIEnv *env, jclass clazz) {
    return g_virtual_mouse_y;
}

// ===== 供 C# P/Invoke 调用的导出函数 =====

// 计算总触屏点数（真实 + 虚拟）
static int getTotalTouchCount() {
    int total = g_touch_data.count;
    for (int i = 0; i < MAX_VIRTUAL_TOUCHES; i++) {
        if (g_virtual_touch.active[i]) {
            total++;
        }
    }
    return total;
}

__attribute__((visibility("default")))
int RAL_GetTouchCount() {
    int count = getTotalTouchCount();
    // 调试日志（每60次调用打印一次，避免日志过多）
    static int callCount = 0;
    if (++callCount % 60 == 0) {
        LOGI("RAL_GetTouchCount: total=%d (real=%d, virtual=%d)", 
            count, g_touch_data.count, 
            getTotalTouchCount() - g_touch_data.count);
    }
    return count;
}

__attribute__((visibility("default")))
float RAL_GetTouchX(int index) {
    // 先检查真实触屏
    if (index >= 0 && index < g_touch_data.count) {
        // 返回归一化坐标（0-1），C# 会乘以屏幕宽度
        return g_touch_data.x[index];
    }

    // 然后检查虚拟触屏
    int virtualIndex = index - g_touch_data.count;
    int currentVirtual = 0;
    for (int i = 0; i < MAX_VIRTUAL_TOUCHES; i++) {
        if (g_virtual_touch.active[i]) {
            if (currentVirtual == virtualIndex) {
                // 虚拟触屏存储的也是归一化坐标，直接返回
                return g_virtual_touch.x[i];
            }
            currentVirtual++;
        }
    }

    return 0.0f;
}

__attribute__((visibility("default")))
float RAL_GetTouchY(int index) {
    // 先检查真实触屏
    if (index >= 0 && index < g_touch_data.count) {
        // 返回归一化坐标（0-1），C# 会乘以屏幕高度
        return g_touch_data.y[index];
    }
    
    // 然后检查虚拟触屏
    int virtualIndex = index - g_touch_data.count;
    int currentVirtual = 0;
    for (int i = 0; i < MAX_VIRTUAL_TOUCHES; i++) {
        if (g_virtual_touch.active[i]) {
            if (currentVirtual == virtualIndex) {
                // 虚拟触屏存储的也是归一化坐标，直接返回
                return g_virtual_touch.y[i];
            }
            currentVirtual++;
        }
    }
    
    return 0.0f;
}

__attribute__((visibility("default")))
int RAL_GetScreenWidth() {
    return g_touch_data.screen_width;
}

__attribute__((visibility("default")))
int RAL_GetScreenHeight() {
    return g_touch_data.screen_height;
}

// ===== 虚拟鼠标位置导出函数（供 C# 调用）=====

__attribute__((visibility("default")))
int RAL_IsVirtualMouseActive() {
    return g_virtual_mouse_active;
}

__attribute__((visibility("default")))
float RAL_GetVirtualMouseX() {
    return g_virtual_mouse_x;
}

__attribute__((visibility("default")))
float RAL_GetVirtualMouseY() {
    return g_virtual_mouse_y;
}

