实现 OSMesa + Zink 渲染支持，修复黑屏和颜色问题

## 主要更新

### OSMesa + Zink 渲染支持
- **实现完整的 OSMesa 渲染桥接**：支持通过 OSMesa 在 Android 上使用 Zink (Vulkan) 渲染器
- **修复黑屏问题**：将 OSMesa 上下文从 Core Profile 改为 Compatibility Profile，解决 FNA3D/MojoShader 使用遗留 OpenGL 函数导致的 GL_INVALID_OPERATION 错误
- **修复颜色问题**：使用 OSMESA_BGRA 格式和 WINDOW_FORMAT_RGBA_8888，确保颜色通道顺序正确

### 核心修改

#### FNA3D 驱动修改 (`FNA3D_Driver_OpenGL.c`)
- 检测 OSMesa 模式并强制禁用 faux backbuffer，确保直接渲染到主缓冲区
- 动态加载并调用 `osm_ensure_current()` 确保 OSMesa 上下文在着色器编译线程上正确激活
- 使用 `OSMesaGetProcAddress` 获取 OpenGL 函数指针，确保所有 GL 调用路由到 OSMesa

#### SDL Android 修改
- **SDL_androidgl.c**：
  - OSMesa 模式下返回虚拟 GL 上下文，跳过 EGL 上下文创建
  - 实现 `Android_GLES_GetDrawableSize` 正确报告 ANativeWindow 尺寸
  - `Android_GLES_GetProcAddress` 使用 `OSMesaGetProcAddress` 获取函数指针
  - `Android_GLES_SwapWindow` 调用 `osm_swap_buffers()` 进行缓冲区交换
- **SDL_androidwindow.c**：OSMesa 模式下跳过 EGL Surface 创建，避免与 ANativeWindow_lock 冲突

#### OSMesa 桥接 (`osm_bridge.c`)
- 使用 Compatibility Profile (OpenGL 4.6) 替代 Core Profile
- 实现双缓冲模式：先刷新并提交当前帧，再锁定下一帧的缓冲区
- 自动设置 viewport 匹配缓冲区尺寸，解决初始 1x1 视口问题
- 在 `osm_ensure_current` 中提前锁定并准备原生表面，确保第一帧就能正确渲染

### 代码清理
- **删除旧的 SettingsManager**：移除 `app/src/main/java/com/app/ralaunch/utils/SettingsManager.java`
- **统一使用新的 SettingsManager**：所有代码统一使用 `com.app.ralaunch.data.SettingsManager`
- 修复 `GameLauncher.java` 中的引用

### 技术细节
- OSMesa 上下文使用 Compatibility Profile 以支持 FNA3D 的遗留 OpenGL API
- 颜色格式：OSMESA_BGRA (0x1) + WINDOW_FORMAT_RGBA_8888，确保 Android 正确显示颜色
- 线程安全：使用 thread-local storage 管理 OSMesa 上下文，支持多线程渲染
- 缓冲区管理：实现双缓冲模式，避免渲染和显示冲突

### 测试结果
- ✅ 游戏画面正常显示（tModLoader 主菜单和游戏界面）
- ✅ 无 GL 错误（之前有 GL_INVALID_OPERATION 0x502）
- ✅ 颜色显示正确（修复 BGRA/RGBA 通道顺序）
- ✅ 性能稳定（60fps 流畅运行）

### 相关文件
- `app/src/main/cpp/FNA3D/src/FNA3D_Driver_OpenGL.c`
- `app/src/main/cpp/SDL/src/video/android/SDL_androidgl.c`
- `app/src/main/cpp/SDL/src/video/android/SDL_androidwindow.c`
- `app/src/main/cpp/osmesa/osm_bridge.c`
- `app/src/main/cpp/osmesa/osm_bridge.h`
- `app/src/main/java/com/app/ralaunch/core/GameLauncher.java`
