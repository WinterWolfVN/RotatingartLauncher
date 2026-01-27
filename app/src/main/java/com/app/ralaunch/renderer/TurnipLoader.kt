package com.app.ralaunch.renderer

import android.system.ErrnoException
import android.system.Os
import com.app.ralaunch.utils.AppLogger

/**
 * Turnip 驱动加载器
 */
object TurnipLoader {
    private const val TAG = "TurnipLoader"
    
    @JvmStatic
    var isTurnipLoaded: Boolean = false
        private set

    @JvmStatic
    var turnipHandle: Long = 0
        private set

    @JvmStatic
    var vulkanLoaderHandle: Long = 0
        private set

    @JvmStatic
    var vkGetInstanceProcAddr: Long = 0
        private set

    init {
        try {
            System.loadLibrary("main")
            AppLogger.info(TAG, "libmain.so loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            AppLogger.error(TAG, "Failed to load libmain.so: ${e.message}")
        }
    }

    @JvmStatic
    fun loadTurnip(nativeLibDir: String, cacheDir: String?): Boolean {
        if (isTurnipLoaded) {
            AppLogger.info(TAG, "Turnip already loaded")
            return true
        }

        return try {
            cacheDir?.let {
                try {
                    Os.setenv("TMPDIR", it, true)
                    AppLogger.info(TAG, "Set TMPDIR=$it")
                } catch (e: ErrnoException) {
                    AppLogger.error(TAG, "Failed to set TMPDIR: ${e.message}")
                }
            }

            val success = nativeLoadTurnip(nativeLibDir, cacheDir)
            if (success) {
                turnipHandle = nativeGetTurnipHandle()
                vulkanLoaderHandle = nativeGetVulkanLoaderHandle()
                vkGetInstanceProcAddr = nativeGetVkGetInstanceProcAddr()
                isTurnipLoaded = true

                AppLogger.info(TAG, "Turnip loaded successfully (zomdroid-style)!")
                AppLogger.info(TAG, "  Turnip handle: 0x${turnipHandle.toString(16)}")
                AppLogger.info(TAG, "  Vulkan loader handle: 0x${vulkanLoaderHandle.toString(16)}")
                AppLogger.info(TAG, "  vkGetInstanceProcAddr: 0x${vkGetInstanceProcAddr.toString(16)}")

                try {
                    Os.setenv("TURNIP_HANDLE", turnipHandle.toString(16), true)
                    Os.setenv("VULKAN_PTR", "0x${vulkanLoaderHandle.toString(16)}", true)
                    Os.setenv("VK_GET_INSTANCE_PROC_ADDR", "0x${vkGetInstanceProcAddr.toString(16)}", true)
                } catch (e: ErrnoException) {
                    AppLogger.error(TAG, "Failed to set environment variables: ${e.message}")
                }
            } else {
                AppLogger.error(TAG, "Failed to load Turnip")
            }
            success
        } catch (e: UnsatisfiedLinkError) {
            AppLogger.error(TAG, "Native method not found: ${e.message}")
            false
        }
    }

    private external fun nativeLoadTurnip(nativeLibDir: String, cacheDir: String?): Boolean
    private external fun nativeGetTurnipHandle(): Long
    private external fun nativeGetVulkanLoaderHandle(): Long
    private external fun nativeGetVkGetInstanceProcAddr(): Long
}
