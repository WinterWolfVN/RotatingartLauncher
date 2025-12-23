package com.app.ralaunch.renderer;

import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import com.app.ralaunch.utils.AppLogger;

public class TurnipLoader {
    private static final String TAG = "TurnipLoader";
    private static boolean sTurnipLoaded = false;
    private static long sTurnipHandle = 0;
    private static long sVulkanLoaderHandle = 0;
    private static long sVkGetInstanceProcAddr = 0;

    // 确保 libmain.so 已加载（包含 JNI native 方法实现）
    static {
        try {
            System.loadLibrary("main");
            AppLogger.info(TAG, "libmain.so loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            AppLogger.error(TAG, "Failed to load libmain.so: " + e.getMessage());
        }
    }

    /**
     * Load Turnip driver using zomdroid-style linker hook approach
     * @param nativeLibDir The directory containing libvulkan_freedreno.so
     * @param cacheDir The cache directory for SONAME-patched libvulkan.so
     * @return true if Turnip loaded successfully
     */
    public static boolean loadTurnip(String nativeLibDir, String cacheDir) {
        if (sTurnipLoaded) {
            AppLogger.info(TAG, "Turnip already loaded");
            return true;
        }

        try {
            // Set TMPDIR for native code
            if (cacheDir != null) {
                try {
                    Os.setenv("TMPDIR", cacheDir, true);
                    AppLogger.info(TAG, "Set TMPDIR=" + cacheDir);
                } catch (ErrnoException e) {
                    AppLogger.error(TAG, "Failed to set TMPDIR: " + e.getMessage());
                }
            }
            
            // Load Turnip using zomdroid-style approach
            boolean success = nativeLoadTurnip(nativeLibDir, cacheDir);
            if (success) {
                sTurnipHandle = nativeGetTurnipHandle();
                sVulkanLoaderHandle = nativeGetVulkanLoaderHandle();
                sVkGetInstanceProcAddr = nativeGetVkGetInstanceProcAddr();
                sTurnipLoaded = true;
                
                AppLogger.info(TAG, "Turnip loaded successfully (zomdroid-style)!");
                AppLogger.info(TAG, "  Turnip handle: 0x" + Long.toHexString(sTurnipHandle));
                AppLogger.info(TAG, "  Vulkan loader handle: 0x" + Long.toHexString(sVulkanLoaderHandle));
                AppLogger.info(TAG, "  vkGetInstanceProcAddr: 0x" + Long.toHexString(sVkGetInstanceProcAddr));
                
                // Set environment variables for DXVK
                try {
                    Os.setenv("TURNIP_HANDLE", Long.toHexString(sTurnipHandle), true);
                    Os.setenv("VULKAN_PTR", "0x" + Long.toHexString(sVulkanLoaderHandle), true);
                    Os.setenv("VK_GET_INSTANCE_PROC_ADDR", "0x" + Long.toHexString(sVkGetInstanceProcAddr), true);
                } catch (ErrnoException e) {
                    AppLogger.error(TAG, "Failed to set environment variables: " + e.getMessage());
                }
            } else {
                AppLogger.error(TAG, "Failed to load Turnip");
            }
            return success;
        } catch (UnsatisfiedLinkError e) {
            AppLogger.error(TAG, "Native method not found: " + e.getMessage());
            return false;
        }
    }

    public static boolean isTurnipLoaded() {
        return sTurnipLoaded;
    }

    public static long getTurnipHandle() {
        return sTurnipHandle;
    }

    public static long getVulkanLoaderHandle() {
        return sVulkanLoaderHandle;
    }

    public static long getVkGetInstanceProcAddr() {
        return sVkGetInstanceProcAddr;
    }

    // Native methods - implemented in turnip_loader_nsbypass.cpp
    private static native boolean nativeLoadTurnip(String nativeLibDir, String cacheDir);
    private static native long nativeGetTurnipHandle();
    private static native long nativeGetVulkanLoaderHandle();
    private static native long nativeGetVkGetInstanceProcAddr();
}
