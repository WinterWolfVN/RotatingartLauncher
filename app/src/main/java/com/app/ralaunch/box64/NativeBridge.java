package com.app.ralaunch.box64;

import android.content.Context;

/**
 * JNI wrapper for glibc-bridge
 * 
 * Note: This class is now only used for initialization (init).
 * Box64 is launched directly via Box64Helper.runBox64InProcess() which calls
 * Box64 library functions (initialize + emulate) instead of running the executable.
 */
public class NativeBridge {
    private static boolean isLoaded = false;
    
    public static synchronized void loadLibrary() {
        if (!isLoaded) {
            System.loadLibrary("glibc_bridge");
            isLoaded = true;
        }
    }

    /**
     * Initialize and extract rootfs from assets
     * @param context Android context (for accessing assets)
     * @param filesDir Application files directory
     * @return 0 on success, negative on error
     */
    public static native int init(Context context, String filesDir);

    /**
     * @deprecated Use Box64Helper.runBox64InProcess() instead.
     * This method runs Box64 as an executable via fork(), which breaks JNI context.
     */
    @Deprecated
    public static native int run(String programPath, String[] args, String rootfsPath);

    /**
     * @deprecated Use Box64Helper.runBox64InProcess() instead.
     * This method runs Box64 as an executable via fork(), which breaks JNI context.
     */
    @Deprecated
    public static native int runWithEnv(String programPath, String[] args, String[] envp, String rootfsPath);
}

