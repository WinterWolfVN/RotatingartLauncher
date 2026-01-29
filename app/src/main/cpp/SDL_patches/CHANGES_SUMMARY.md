# SDL2 修改汇总 (RALCore v2.30.1)

基于 SDL 2.30.1 版本，对比官方源码的所有修改。

## 📊 修改统计

- **修改的文件**: 20 个
- **新增的文件**: 2 个
- **补丁总大小**: ~188KB

---

## 🔧 修改详情

### 1. `src/SDL.c`

**修改类型**: 行为修改  
**行数**: 1 行

```c
// 原始代码
return "Android";

// 修改后
return "Linux";  // 伪装为 Linux 平台
```

**目的**: 让游戏认为运行在 Linux 上，避免 Android 特有的行为/限制

---

### 2. `src/SDL_assert.c`

**修改类型**: 行为修改  
**行数**: 1 行

```c
// 原始代码
SDL_MinimizeWindow(window);

// 修改后 (注释掉)
// SDL_MinimizeWindow(window);
```

**目的**: 防止断言时窗口被最小化，保持游戏画面

---

### 3. `src/audio/aaudio/SDL_aaudio.c`

**修改类型**: 功能增强  
**行数**: ~25 行

**修改内容**:
1. 注释掉两处 `SDL_assert` 检查（允许多设备）
2. 低延迟模式改为由环境变量 `SDL_AAUDIO_LOW_LATENCY=1` 控制

**目的**: 
- 避免多音频设备时的断言失败
- 某些设备上低延迟模式有问题，改为可选

---

### 4. `src/core/android/SDL_android.c` ⭐ 核心修改

**修改类型**: 功能扩展  
**行数**: ~200 行

**新增 JNI 方法**:

| 方法名 | 功能 |
|--------|------|
| `nativeAndroidJNISetEnvCurrent` | Box64 线程 JNI 支持 |
| `nativeAndroidJNISetEnvNull` | 清理 JNI 环境 |
| `onNativeMouseDirect` | 直接鼠标事件（不检查状态）|
| `onNativeMouseButton` | 鼠标按钮 + 位置 |
| `onNativeMouseButtonOnly` | 只发送按钮（不移动光标）|
| `nativeGetMouseStateX/Y` | 获取鼠标位置 |
| `nativeConsumeFingerTouch` | 占用触摸点 |
| `nativeReleaseFingerTouch` | 释放触摸点 |
| `nativeClearConsumedFingers` | 清除所有占用 |

**修改的函数**:
- `Android_AddJoystick`: 增加 `can_rumble` 参数
- `Android_JNI_GetNativeWindow`: 增加调试日志
- `Android_JNI_GetManifestEnvironmentVariables`: Box64 兼容性检查
- `Android_JNI_HapticRumble`: 新增双马达震动支持
- `Android_JNI_SetupThread`: 增加 `__attribute__((visibility("default")))`

---

### 5. `src/core/android/SDL_android.h`

**修改类型**: 接口扩展  
**行数**: 1 行

```c
// 新增
void Android_JNI_HapticRumble(int device_id, float low_freq, float high_freq, int length);
```

---

### 6. `src/events/SDL_mouse.c`

**修改类型**: 功能扩展  
**行数**: ~60 行

**新增功能**:
1. **虚拟鼠标范围限制** - 限制鼠标移动范围（用于游戏控制）
2. **多点触控状态检查绕过** - 当 `SDL_TOUCH_MOUSE_MULTITOUCH=1` 时

**新增导出函数**:
```c
DECLSPEC void SDLCALL SDL_SetVirtualMouseRangeEnabled(SDL_bool enabled);
DECLSPEC void SDLCALL SDL_SetVirtualMouseScreenSize(int width, int height);
DECLSPEC void SDLCALL SDL_SetVirtualMouseRange(float left, float top, float right, float bottom);
DECLSPEC void SDLCALL SDL_ApplyVirtualMouseRangeLimit(int *mouseX, int *mouseY);
```

---

### 7. `src/events/SDL_touch.c` ⭐ 核心修改

**修改类型**: 功能扩展  
**行数**: ~180 行

**新增功能**:
1. **多点触控转鼠标** - 每个手指独立发送鼠标事件
2. **虚拟控件触摸过滤** - 被占用的触摸点不转鼠标

**新增变量**:
```c
#define MAX_TRACKED_FINGERS 10
static int multitouch_finger_count;
static SDL_FingerID multitouch_fingers[MAX_TRACKED_FINGERS];
static SDL_bool multitouch_enabled;
static SDL_FingerID multitouch_active_finger;

#define MAX_CONSUMED_FINGERS 10
static int consumed_finger_count;
static int consumed_fingers[MAX_CONSUMED_FINGERS];
```

**新增导出函数**:
```c
void SDL_ConsumeFingerTouch(int fingerId);
void SDL_ReleaseFingerTouch(int fingerId);
void SDL_ClearConsumedFingers(void);
```

---

### 8. `src/joystick/android/SDL_sysjoystick.c`

**修改类型**: 功能扩展  
**行数**: ~20 行

**修改内容**:
1. `Android_AddJoystick` 增加 `can_rumble` 参数
2. `ANDROID_JoystickRumble` 实现双马达震动

```c
// 原始
int Android_AddJoystick(..., int nballs);
static int ANDROID_JoystickRumble(...) { return SDL_Unsupported(); }

// 修改后
int Android_AddJoystick(..., int nballs, SDL_bool can_rumble);
static int ANDROID_JoystickRumble(...) {
    // 实际调用 Android_JNI_HapticRumble
}
```

---

### 9. `src/joystick/android/SDL_sysjoystick_c.h`

**修改类型**: 接口修改  
**行数**: 2 行

```c
// 函数签名增加 can_rumble
extern int Android_AddJoystick(..., SDL_bool can_rumble);

// 结构体增加字段
typedef struct SDL_joylist_item {
    // ...
    SDL_bool can_rumble;  // 新增
} SDL_joylist_item;
```

---

### 10. `src/main/android/SDL_android_main.c`

**修改类型**: 功能扩展  
**行数**: ~18 行

**新增功能**: 自定义入口点支持

```c
typedef void (*Main)();
Main CurrentMain;

__attribute__((visibility("default"))) void SetMain(Main main);
__attribute__((visibility("default"))) int SDL_main(int argc, char* argv[]);
```

**目的**: 允许 Box64/dotnet 设置自己的入口函数

---

### 11. `src/sensor/android/SDL_androidsensor.c`

**修改类型**: API 修复  
**行数**: 1 行

```c
// 原始
ALooper_pollAll(0, ...)

// 修改后
ALooper_pollOnce(0, ...)
```

**目的**: 兼容性修复

---

### 12. `src/video/SDL_egl.c` ⭐ 核心修改

**修改类型**: 功能扩展  
**行数**: ~150 行

**修改内容**:
1. `SDL_EGL_GetProcAddress` - OSMesa 优先使用 `OSMesaGetProcAddress`
2. `SDL_EGL_PrivateChooseConfig` - 注释掉 `EGL_RENDERABLE_TYPE` 设置
3. `SDL_EGL_CreateContext` - gl4es/zink/OSMesa 特殊处理
   - gl4es: 使用 GLES 上下文
   - zink + OSMesa: Android 上使用 ES API（OSMesa 内部处理桌面 GL）
   - API 绑定错误处理

---

### 13. `src/video/android/SDL_androidgl.c` ⭐ 核心修改

**修改类型**: 功能扩展  
**行数**: ~250 行

**修改内容**:
1. `Android_GLES_MakeCurrent` - OSMesa 上下文初始化
2. `Android_GLES_CreateContext` - OSMesa 返回假上下文
3. `Android_GLES_SwapWindow` - OSMesa 使用 `osm_swap_buffers`
4. `Android_GLES_LoadLibrary` - 支持动态渲染器库
5. `Android_GLES_GetProcAddress` - OSMesa/自定义 GL 库支持
6. 新增 `Android_GLES_GetDrawableSize` - OSMesa 从 ANativeWindow 获取尺寸

---

### 14. `src/video/android/SDL_androidgl.h`

**修改类型**: 接口扩展  
**行数**: 7 行

新增函数声明:
```c
int Android_GLES_LoadLibrary(_THIS, const char *path);
void *Android_GLES_GetProcAddress(_THIS, const char *proc);
void Android_GLES_UnloadLibrary(_THIS);
int Android_GLES_SetSwapInterval(_THIS, int interval);
int Android_GLES_GetSwapInterval(_THIS);
void Android_GLES_DeleteContext(_THIS, SDL_GLContext context);
void Android_GLES_GetDrawableSize(_THIS, SDL_Window *window, int *w, int *h);
```

---

### 15. `src/video/android/SDL_androidmouse.c`

**修改类型**: 功能扩展  
**行数**: ~60 行

新增函数:
```c
void Android_OnMouseDirect(SDL_Window *window, int state, int action, 
                           float x, float y, SDL_bool relative);
void Android_OnMouseButtonDirect(SDL_Window *window, int sdlButton, 
                                  int pressed, float x, float y);
void Android_OnMouseButtonOnly(SDL_Window *window, int sdlButton, int pressed);
```

---

### 16. `src/video/android/SDL_androidmouse.h`

**修改类型**: 接口扩展  
**行数**: 3 行

---

### 17. `src/video/android/SDL_androidtouch.c`

**修改类型**: 调试增强  
**行数**: ~15 行

新增调试日志（默认禁用）

---

### 18. `src/video/android/SDL_androidvideo.c` ⭐ 核心修改

**修改类型**: 功能扩展  
**行数**: ~90 行

**修改内容**:
1. `Android_CreateDevice` - 集成动态渲染器加载
2. `Android_CreateDevice` - 新增 `SetWindowSize`/`SetWindowPosition`
3. `Android_VideoInit` - 添加额外显示模式（480p ~ 1920p）

---

### 19. `src/video/android/SDL_androidwindow.c`

**修改类型**: 功能扩展  
**行数**: ~60 行

**修改内容**:
1. `Android_CreateWindow` - 增加详细日志 + OSMesa 跳过 EGL surface
2. 新增 `Android_SetWindowSize` - 强制全屏尺寸
3. 新增 `Android_SetWindowPosition` - 强制位置 (0,0)

---

### 20. `src/video/android/SDL_androidwindow.h`

**修改类型**: 接口扩展  
**行数**: 2 行

---

### 21. `src/video/android/SDL_androidrenderer.c` 🆕 新增

**文件类型**: 全新文件  
**行数**: ~340 行

**功能**: 动态渲染器加载系统

支持的渲染器:
- `native`: 系统默认 EGL/GLES
- `gl4es`: OpenGL 2.1 → GLES 2.0 翻译
- `angle`: OpenGL ES over Vulkan
- `mobileglues`: 移动端 GL
- `zink`: OpenGL over Vulkan (via OSMesa)
- `dxvk`: D3D11 over Vulkan

---

### 22. `src/video/android/SDL_androidrenderer.h` 🆕 新增

**文件类型**: 全新文件  
**行数**: ~77 行

---

## 🎮 功能分类

### A. 虚拟控件支持
- 多点触控转鼠标
- 触摸点占用管理
- 直接鼠标控制
- 虚拟鼠标范围限制

### B. 渲染器支持
- 动态渲染器加载
- gl4es 支持
- OSMesa/Zink 支持
- DXVK 支持

### C. 手柄支持
- 双马达震动
- 震动能力检测

### D. Box64 兼容
- JNI 线程管理
- 平台伪装
- 自定义入口点

### E. 游戏兼容性
- 强制全屏窗口
- 额外显示模式
- 断言不最小化

---

## 📝 迁移到 SDL3 检查清单

- [ ] SDL3 新的 JNI 架构适配
- [ ] 触摸/鼠标事件系统变化
- [ ] 渲染器 API 变化
- [ ] 手柄震动 API 变化
- [ ] 窗口管理变化
