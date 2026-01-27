package com.app.ralaunch.box64

import android.content.Context

/**
 * JNI wrapper for glibc-bridge
 *
 * Note: This class is now only used for initialization (init).
 * Box64 is launched directly via Box64Helper.runBox64InProcess() which calls
 * Box64 library functions (initialize + emulate) instead of running the executable.
 */
object NativeBridge {
    private var isLoaded = false

    @JvmStatic
    @Synchronized
    fun loadLibrary() {
        if (!isLoaded) {
            System.loadLibrary("glibc_bridge")
            isLoaded = true
        }
    }

    /**
     * Initialize and extract rootfs from assets
     * @param context Android context (for accessing assets)
     * @param filesDir Application files directory
     * @return 0 on success, negative on error
     */
    @JvmStatic
    external fun init(context: Context, filesDir: String): Int

    /**
     * @deprecated Use Box64Helper.runBox64InProcess() instead.
     * This method runs Box64 as an executable via fork(), which breaks JNI context.
     */
    @JvmStatic
    @Deprecated("Use Box64Helper.runBox64InProcess() instead")
    external fun run(programPath: String, args: Array<String>, rootfsPath: String): Int

    /**
     * @deprecated Use Box64Helper.runBox64InProcess() instead.
     * This method runs Box64 as an executable via fork(), which breaks JNI context.
     */
    @JvmStatic
    @Deprecated("Use Box64Helper.runBox64InProcess() instead")
    external fun runWithEnv(programPath: String, args: Array<String>, envp: Array<String>, rootfsPath: String): Int
}
