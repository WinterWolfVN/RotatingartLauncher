package com.app.ralaunch.core.platform.runtime.renderer

expect object RendererRegistry {
    fun normalizeRendererId(raw: String?): String
}
