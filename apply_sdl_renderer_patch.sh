#!/bin/bash

#
# SDL Dynamic Renderer Patch Application Script
#
# 自动应用动态渲染器加载补丁到 SDL_androidvideo.c
#

SDL_VIDEO_FILE="app/src/main/cpp/SDL/src/video/android/SDL_androidvideo.c"

echo "================================================"
echo "  SDL Dynamic Renderer Patch Application"
echo "================================================"

# 检查文件是否存在
if [ ! -f "$SDL_VIDEO_FILE" ]; then
    echo "❌ Error: $SDL_VIDEO_FILE not found!"
    exit 1
fi

# 备份原文件
echo "📦 Creating backup..."
cp "$SDL_VIDEO_FILE" "${SDL_VIDEO_FILE}.backup"
echo "   ✓ Backup saved to ${SDL_VIDEO_FILE}.backup"

# 创建临时补丁文件
cat > /tmp/sdl_renderer.patch << 'EOF'
    /* ================================================================
     * 🔥 Dynamic Renderer Loading (lwjgl3 + PojavLauncher style)
     * ================================================================
     *
     * 支持运行时动态切换渲染器，无需重新编译
     *
     * 环境变量：
     *   SDL_RENDERER 或 FNA3D_OPENGL_DRIVER
     *     - native: 系统默认 (libEGL.so + libGLESv2.so)
     *     - gl4es: OpenGL 2.1 翻译层
     *     - angle: OpenGL ES over Vulkan
     *     - zink: OpenGL over Vulkan
     *
     * 实现原理：
     *   1. 通过 dlopen(RTLD_GLOBAL) 预加载渲染器库
     *   2. 通过 LD_PRELOAD 劫持 SDL 的 dlopen("libEGL.so")
     *   3. 所有渲染器都提供标准 EGL 接口
     *   4. SDL 无感知，直接使用 Android_GLES_* 函数
     */

    /* 从环境变量读取渲染器配置 */
    const char* renderer_name = SDL_getenv("SDL_RENDERER");
    if (!renderer_name || renderer_name[0] == '\0') {
        renderer_name = SDL_getenv("FNA3D_OPENGL_DRIVER");
    }
    if (!renderer_name || renderer_name[0] == '\0') {
        renderer_name = "native";  /* 默认使用系统渲染器 */
    }

    /* 动态加载渲染器 */
    if (!Android_LoadRenderer(renderer_name)) {
        SDL_LogWarn(SDL_LOG_CATEGORY_VIDEO,
                    "Failed to load renderer '%s', falling back to native",
                    renderer_name);
        Android_LoadRenderer("native");
    }

    /* 设置 GL 函数指针 */
    /* 由于使用 LD_PRELOAD，所有渲染器都提供标准 EGL 接口 */
    if (!Android_SetupGLFunctions(device)) {
        SDL_LogError(SDL_LOG_CATEGORY_VIDEO, "Failed to setup GL functions");
        SDL_free(data);
        SDL_free(device);
        return NULL;
    }

    SDL_LogInfo(SDL_LOG_CATEGORY_VIDEO,
                "✅ Renderer initialized: %s",
                Android_GetCurrentRenderer());
EOF

echo "🔧 Applying patch..."

# 使用 sed 删除旧代码并插入新代码
# 删除第 133-233 行（所有 #if defined 的 GL 指针设置代码）
sed -i '133,233d' "$SDL_VIDEO_FILE"

# 在第 133 行插入新代码
sed -i '132r /tmp/sdl_renderer.patch' "$SDL_VIDEO_FILE"

echo "   ✓ Patch applied successfully"

# 验证
echo "🔍 Verifying patch..."
if grep -q "Android_LoadRenderer" "$SDL_VIDEO_FILE"; then
    echo "   ✓ Verification passed"
    echo ""
    echo "✅ Patch applied successfully!"
    echo ""
    echo "Next steps:"
    echo "  1. Update CMakeLists.txt files"
    echo "  2. Integrate into GameActivity.java"
    echo "  3. Build and test"
else
    echo "   ❌ Verification failed!"
    echo "   Restoring backup..."
    cp "${SDL_VIDEO_FILE}.backup" "$SDL_VIDEO_FILE"
    exit 1
fi

# 清理
rm /tmp/sdl_renderer.patch

echo "================================================"
