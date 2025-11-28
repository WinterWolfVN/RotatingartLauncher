package com.app.ralaunch.renderer;

import android.util.Log;
import android.view.Surface;

/**
 * OSMesa renderer wrapper for zink
 * 
 * This class provides a Java interface to the OSMesa video interface,
 * allowing zink renderer to use OSMesa for off-screen rendering to
 * Android Native Window.
 */
public class OSMRenderer {
    private static final String TAG = "OSMRenderer";
    private static boolean sInitialized = false;

    static {
        try {
            System.loadLibrary("main");
            Log.i(TAG, "OSMRenderer native library loaded");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load OSMesa renderer native library", e);
        }
    }

    /**
     * Initialize OSMesa renderer
     * @param surface Android Surface (can be null for offscreen rendering)
     * @return true on success, false on failure
     */
    public static boolean init(Surface surface) {
        if (sInitialized) {
            Log.w(TAG, "OSMRenderer already initialized");
            if (surface != null) {
                setWindow(surface);
            }
            return true;
        }

        boolean result = nativeInit(surface);
        if (result) {
            sInitialized = true;
            Log.i(TAG, "OSMRenderer initialized successfully");
        } else {
            Log.e(TAG, "Failed to initialize OSMesa renderer");
        }
        return result;
    }

    /**
     * Cleanup OSMesa renderer
     */
    public static void cleanup() {
        if (!sInitialized) {
            return;
        }

        nativeCleanup();
        sInitialized = false;
        Log.i(TAG, "OSMRenderer cleaned up");
    }

    /**
     * Swap buffers (render to window)
     */
    public static void swapBuffers() {
        if (!sInitialized) {
            Log.w(TAG, "OSMRenderer not initialized, cannot swap buffers");
            return;
        }
        nativeSwapBuffers();
    }

    /**
     * Set swap interval (vsync)
     * @param interval Swap interval (0 = immediate, 1 = vsync)
     */
    public static void setSwapInterval(int interval) {
        if (!sInitialized) {
            Log.w(TAG, "OSMRenderer not initialized, cannot set swap interval");
            return;
        }
        nativeSetSwapInterval(interval);
    }

    /**
     * Check if OSMesa renderer is available
     * @return true if available, false otherwise
     */
    public static boolean isAvailable() {
        return nativeIsAvailable();
    }

    /**
     * Set renderer window
     * @param surface Android Surface
     */
    public static void setWindow(Surface surface) {
        if (!sInitialized) {
            Log.w(TAG, "OSMRenderer not initialized, cannot set window");
            return;
        }
        nativeSetWindow(surface);
    }

    /**
     * Check if renderer is initialized
     * @return true if initialized, false otherwise
     */
    public static boolean isInitialized() {
        return sInitialized;
    }

    /**
     * Load Vulkan library (must be called before OSMesa initialization for zink)
     * @return true on success, false on failure
     */
    public static boolean nativeLoadVulkan() {
        return nativeLoadVulkanInternal();
    }

    // Native methods
    private static native boolean nativeInit(Surface surface);
    private static native void nativeCleanup();
    private static native void nativeSwapBuffers();
    private static native void nativeSetSwapInterval(int interval);
    private static native boolean nativeIsAvailable();
    private static native void nativeSetWindow(Surface surface);
    private static native boolean nativeLoadVulkanInternal();
}

