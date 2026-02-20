package com.app.ralaunch.core.platform.runtime.renderer

import android.content.Context

data class RendererInfo(
    val id: String,
    val displayName: String?,
    val description: String?,
    // EGL 库文件名 (null = 系统默认)
    val eglLibrary: String?,
    // GLES 库文件名 (null = 系统默认)
    val glesLibrary: String?,
    // 是否需要通过 LD_PRELOAD 加载
    val needsPreload: Boolean,
    // 最低 Android 版本
    val minAndroidVersion: Int,
    // 渲染器专属环境变量配置
    val configureEnv: (context: Context, env: MutableMap<String, String?>) -> Unit = { _, _ -> }
)
