# SDL2 补丁文件 (RALCore 扩展)

本目录包含 RotatingartLauncher 对 SDL2 的所有自定义修改。

## 🎯 设计目标

1. **保持 SDL 源码不变** - 便于升级 SDL2/SDL3
2. **所有修改以独立文件/头文件存在** - 通过编译时链接覆盖
3. **清晰的功能分组** - 便于维护和迁移

## 📁 文件结构

```
SDL_patches/
├── README.md                       # 本文档
├── CMakeLists.txt                  # 补丁编译配置
├── ral_sdl_config.h               # 全局配置开关
│
├── android/                        # Android 平台相关补丁
│   ├── ral_android_jni.c          # JNI 扩展 (鼠标直接控制, 触摸点管理等)
│   ├── ral_android_jni.h
│   ├── ral_android_mouse.c        # 鼠标扩展函数
│   ├── ral_android_mouse.h
│   ├── ral_android_window.c       # 窗口扩展 (强制全屏等)
│   ├── ral_android_window.h
│   ├── ral_android_renderer.c     # 动态渲染器加载
│   ├── ral_android_renderer.h
│   └── ral_android_gl.c           # GL 扩展 (OSMesa, gl4es 支持)
│
├── input/                          # 输入系统扩展
│   ├── ral_touch_multitouch.c     # 多点触控转鼠标
│   ├── ral_touch_multitouch.h
│   ├── ral_mouse_range.c          # 虚拟鼠标范围限制
│   └── ral_mouse_range.h
│
├── joystick/                       # 手柄扩展
│   ├── ral_joystick_rumble.c      # 手柄震动支持
│   └── ral_joystick_rumble.h
│
└── audio/                          # 音频扩展
    └── ral_aaudio_config.c        # AAudio 配置扩展
```

## 🔧 修改类型分析

### 1. 全新功能 (可完全分离)

| 文件 | 功能 | 分离难度 |
|------|------|---------|
| `SDL_androidrenderer.c/h` | 动态渲染器加载 | ✅ 简单 |
| `ral_mouse_range.c` | 虚拟鼠标范围限制 | ✅ 简单 |

### 2. JNI 扩展 (需要 Java 层配合)

| 原文件 | 新增函数 | 分离方案 |
|--------|----------|----------|
| `SDL_android.c` | `onNativeMouseDirect` | 独立文件 + 注册表 |
| `SDL_android.c` | `onNativeMouseButton` | 独立文件 + 注册表 |
| `SDL_android.c` | `onNativeMouseButtonOnly` | 独立文件 + 注册表 |
| `SDL_android.c` | `nativeConsumeFingerTouch` | 独立文件 + 注册表 |
| `SDL_android.c` | `nativeReleaseFingerTouch` | 独立文件 + 注册表 |
| `SDL_android.c` | `nativeClearConsumedFingers` | 独立文件 + 注册表 |
| `SDL_android.c` | `nativeGetMouseStateX/Y` | 独立文件 + 注册表 |
| `SDL_android.c` | `HapticRumble` | 独立文件 + 注册表 |

### 3. 行为修改 (需要 Hook 或条件编译)

| 原文件 | 修改内容 | 分离方案 |
|--------|----------|----------|
| `SDL.c:569` | `GetPlatform()` 返回 "Linux" | 条件编译宏 |
| `SDL_assert.c` | 禁用最小化 | 条件编译宏 |
| `SDL_aaudio.c` | 低延迟可选 | 环境变量控制 |
| `SDL_androidsensor.c` | `ALooper_pollOnce` | API 兼容修复 |

### 4. 功能增强 (需要修改原函数签名)

| 原文件 | 修改内容 | 分离方案 |
|--------|----------|----------|
| `SDL_sysjoystick.c` | `Android_AddJoystick` +rumble参数 | Wrapper 函数 |
| `SDL_androidwindow.c` | `SetWindowSize/Position` | 新增函数 |
| `SDL_touch.c` | 多点触控支持 | Hook/替换 |

## 🔄 迁移到 SDL3 的策略

### 阶段 1: 准备工作
1. 将所有修改提取到独立文件
2. 使用 CMake 条件编译
3. 编写 SDL 版本兼容层

### 阶段 2: SDL3 适配
1. SDL3 重新设计了 JNI 接口，需要重新实现
2. 触摸/鼠标 API 变化，需要适配新的事件系统
3. 渲染器架构变化，动态加载机制可能需要调整

### 阶段 3: 测试验证
1. 确保所有虚拟控件功能正常
2. 验证手柄支持
3. 测试各渲染器后端

## ⚠️ 注意事项

1. **Java 层必须同步修改** - `SDLActivity.java` 和 `SDLControllerManager.java`
2. **环境变量依赖** - 某些功能依赖运行时环境变量
3. **线程安全** - JNI 调用需要正确的线程绑定

## 📋 修改文件清单

### 修改的源文件 (20个)
```
src/SDL.c                                    # GetPlatform() 返回 "Linux"
src/SDL_assert.c                             # 禁用全屏时最小化
src/audio/aaudio/SDL_aaudio.c               # AAudio 低延迟可选
src/core/android/SDL_android.c              # JNI 扩展 (核心)
src/core/android/SDL_android.h              # 新增函数声明
src/events/SDL_mouse.c                      # 虚拟鼠标范围 + 多点触控状态检查
src/events/SDL_touch.c                      # 多点触控转鼠标 + 虚拟控件过滤
src/joystick/android/SDL_sysjoystick.c      # 手柄震动支持
src/joystick/android/SDL_sysjoystick_c.h    # can_rumble 字段
src/main/android/SDL_android_main.c         # SetMain 入口点
src/sensor/android/SDL_androidsensor.c      # ALooper API 修复
src/video/SDL_egl.c                         # OSMesa/gl4es/zink 支持
src/video/android/SDL_androidgl.c           # GL 加载扩展 (OSMesa等)
src/video/android/SDL_androidgl.h           # 新增函数声明
src/video/android/SDL_androidmouse.c        # 直接鼠标控制函数
src/video/android/SDL_androidmouse.h        # 新增函数声明
src/video/android/SDL_androidtouch.c        # 调试日志
src/video/android/SDL_androidvideo.c        # 动态渲染器 + 显示模式
src/video/android/SDL_androidwindow.c       # 强制全屏窗口
src/video/android/SDL_androidwindow.h       # 新增函数声明
```

### 新增的文件 (2个)
```
src/video/android/SDL_androidrenderer.c     # 动态渲染器加载实现
src/video/android/SDL_androidrenderer.h     # 动态渲染器接口
```
