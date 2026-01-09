package com.app.ralaunch.box64

/**
 * Box64 Helper - 用于直接在进程内运行 Box64（库模式）
 * 
 * Box64 编译为共享库（libbox64.so），直接调用 initialize() 和 emulate() 函数，
 * 而不是运行 box64 可执行文件。这样可以避免 fork() 导致的 JNI 环境问题。
 * 
 * Box64 的 wrapped 库仍然使用 glibc_bridge 的 dlopen 重定向，
 * 确保 SDL2、GL 等原生库正确加载。
 * 
 * 注意：使用前需确保 GameLauncher 已初始化（会加载 libmain.so）
 */
object Box64Helper {
    
    /**
     * 在当前进程中直接运行 Box64（库模式）
     * 
     * 这个方法直接调用 Box64 的 initialize() 和 emulate() 函数（库模式），
     * 而不是运行 box64 可执行文件。
     * Box64 的 wrapped 库仍然使用 glibc_bridge 的 dlopen 重定向。
     * 
     * @param args 参数数组，第一个元素是 x86_64 程序路径
     * @param workDir 工作目录
     * @return 退出代码
     */
    @JvmStatic
    external fun runBox64InProcess(args: Array<String>, workDir: String?): Int
    
}

