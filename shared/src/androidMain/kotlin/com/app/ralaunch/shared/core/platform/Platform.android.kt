package com.app.ralaunch.shared.core.platform

/**
 * Android 平台实现
 */
actual class Platform actual constructor() {
    actual val name: String = "Android ${android.os.Build.VERSION.SDK_INT}"
}
