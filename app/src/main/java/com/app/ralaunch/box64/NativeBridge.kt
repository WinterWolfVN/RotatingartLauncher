package com.app.ralaunch.box64

import android.content.Context

/**
 * JNI wrapper for glibc-bridge
 *
 * 提供两种运行 glibc 程序的方式：
 * 1. run() / runWithEnv() - 运行 glibc ELF 可执行文件（如 glibc 版 Box64）
 * 2. 配合 Box64Helper - bionic 版 Box64 直接调用（仅需 init）
 * 
 * 对于 glibc 版 Box64，使用 GlibcBox64Helper.runBox64() 调用本类的 runWithEnv()
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
     * Initialize glibc_bridge
     * @param context Android context (for accessing assets)
     * @param filesDir Application files directory
     * @return 0 on success, negative on error
     */
    @JvmStatic
    external fun init(context: Context, filesDir: String): Int

    /**
     * Run a glibc ELF executable via glibc_bridge
     * 
     * @param programPath Path to the glibc ELF executable
     * @param args Arguments to pass to the program
     * @param rootfsPath Path to the rootfs directory containing glibc libraries
     * @return Exit code of the program
     */
    @JvmStatic
    external fun run(programPath: String, args: Array<String>, rootfsPath: String): Int

    /**
     * Run a glibc ELF executable via glibc_bridge with custom environment
     * 
     * @param programPath Path to the glibc ELF executable
     * @param args Arguments to pass to the program
     * @param envp Environment variables (format: "KEY=VALUE")
     * @param rootfsPath Path to the rootfs directory containing glibc libraries
     * @return Exit code of the program
     */
    @JvmStatic
    external fun runWithEnv(programPath: String, args: Array<String>, envp: Array<String>, rootfsPath: String): Int

    /**
     * Run a glibc ELF executable via glibc_bridge with FORK mode (isolated execution)
     * 
     * 使用 fork 模式在独立进程中运行，避免影响 Android 主进程的其他线程。
     * 适用于 SteamCMD 等可能与 Android 线程产生冲突的程序。
     * 
     * @param programPath Path to the glibc ELF executable
     * @param args Arguments to pass to the program
     * @param envp Environment variables (format: "KEY=VALUE")
     * @param rootfsPath Path to the rootfs directory containing glibc libraries
     * @return Exit code of the program
     */
    @JvmStatic
    external fun runForked(programPath: String, args: Array<String>, envp: Array<String>, rootfsPath: String): Int
}
