package com.app.ralaunch.shared.core.util

import android.util.Log

/**
 * Android 平台日志实现
 */
actual object AppLog {
    private const val TAG_PREFIX = "RALaunch"
    private var enableDebug = true

    actual fun error(tag: String, message: String) {
        Log.e(formatTag(tag), message)
    }

    actual fun error(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(formatTag(tag), message, throwable)
        } else {
            Log.e(formatTag(tag), message)
        }
    }

    actual fun warn(tag: String, message: String) {
        Log.w(formatTag(tag), message)
    }

    actual fun info(tag: String, message: String) {
        Log.i(formatTag(tag), message)
    }

    actual fun debug(tag: String, message: String) {
        if (enableDebug) {
            Log.d(formatTag(tag), message)
        }
    }

    private fun formatTag(tag: String): String {
        return if (tag.startsWith(TAG_PREFIX)) tag else "$TAG_PREFIX/$tag"
    }

    /**
     * 设置是否启用 debug 日志
     */
    fun setDebugEnabled(enabled: Boolean) {
        enableDebug = enabled
    }
}
