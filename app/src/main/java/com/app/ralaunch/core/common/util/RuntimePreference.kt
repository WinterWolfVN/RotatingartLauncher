package com.app.ralaunch.core.common.util

import android.content.Context
import android.util.Log
import com.app.ralaunch.core.common.SettingsAccess
import org.koin.java.KoinJavaComponent

/**
 * 运行时首选项
 */
object RuntimePreference {
    private const val TAG = "RuntimePreference"

    /**
     * 获取 .NET 运行时根路径
     */
    @JvmStatic
    fun getDotnetRootPath(): String? {
        return try {
            val appContext: Context = KoinJavaComponent.get(Context::class.java)
            val dotnetPath = "${appContext.filesDir.absolutePath}/dotnet"
            Log.d(TAG, "Dotnet root path: $dotnetPath")
            dotnetPath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get dotnet root path", e)
            null
        }
    }

    @JvmStatic
    fun setVerboseLogging(context: Context?, enabled: Boolean) {
        SettingsAccess.isVerboseLogging = enabled
    }

    @JvmStatic
    fun isVerboseLogging(context: Context?): Boolean =
        SettingsAccess.isVerboseLogging
}
