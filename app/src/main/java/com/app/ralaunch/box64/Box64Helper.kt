package com.app.ralaunch.box64

import android.content.Context
import com.app.ralaunch.utils.AppLogger
import java.io.File

/**
 * Box64 Helper - 用于直接在进程内运行 Box64（动态加载模式）
 * 
 * Box64 编译为独立共享库（libbox64.so），运行时通过 dlopen 动态加载。
 * 这种方式允许将 libbox64.so 压缩存储在 assets 中，减小 APK 体积约 50MB。
 * 
 * 使用流程：
 * 1. 调用 ensureBox64Loaded() 确保库已解压并加载
 * 2. 调用 runBox64InProcess() 运行 x86_64 程序
 * 
 * Box64 的 wrapped 库使用 glibc_bridge 的 dlopen 重定向，
 * 确保 SDL2、GL 等原生库正确加载。
 */
object Box64Helper {
    
    private const val TAG = "Box64Helper"
    private const val BOX64_LIB_NAME = "libbox64.so"
    
    /**
     * 确保 Box64 库已加载
     * 如果未加载，会尝试从私有目录加载
     * 
     * @param context Android Context
     * @return true 如果加载成功
     */
    @JvmStatic
    fun ensureBox64Loaded(context: Context): Boolean {
        if (isBox64Loaded()) {
            AppLogger.info(TAG, "Box64 already loaded")
            return true
        }
        
        // 尝试从私有目录加载
        val libPath = File(context.filesDir, "runtime_libs/$BOX64_LIB_NAME")
        if (!libPath.exists()) {
            AppLogger.error(TAG, "Box64 library not found: ${libPath.absolutePath}")
            AppLogger.error(TAG, "Please extract runtime_libs.tar.xz first")
            return false
        }
        
        AppLogger.info(TAG, "Loading Box64 from: ${libPath.absolutePath}")
        return loadBox64Library(libPath.absolutePath)
    }
    
    /**
     * 获取 Box64 库的路径
     */
    @JvmStatic
    fun getBox64LibPath(context: Context): String {
        return File(context.filesDir, "runtime_libs/$BOX64_LIB_NAME").absolutePath
    }
    
    /**
     * 加载 Box64 库（JNI 原生方法）
     * 
     * @param libPath Box64 库的完整路径
     * @return true 如果加载成功
     */
    @JvmStatic
    external fun loadBox64Library(libPath: String): Boolean
    
    /**
     * 检查 Box64 是否已加载（JNI 原生方法）
     */
    @JvmStatic
    external fun isBox64Loaded(): Boolean
    
    /**
     * 在当前进程中直接运行 Box64（库模式）
     * 
     * 注意：调用前必须先调用 ensureBox64Loaded() 确保库已加载
     * 
     * @param args 参数数组，第一个元素是 x86_64 程序路径
     * @param workDir 工作目录
     * @return 退出代码，-2 表示 Box64 未加载
     */
    @JvmStatic
    external fun runBox64InProcess(args: Array<String>, workDir: String?): Int
    
}

