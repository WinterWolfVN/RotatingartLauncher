package com.app.ralaunch.box64

import android.content.Context
import com.app.ralaunch.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Glibc Box64 Helper - 使用 glibc_bridge 运行 glibc 编译的 Box64
 * 
 * 与 Box64Helper（bionic 版本）不同，此类：
 * - 加载 glibc 编译的 Box64 可执行文件
 * - 通过 glibc_bridge 执行（而不是 dlopen）
 * - 支持完整的 glibc 运行时环境
 * 
 * 使用流程：
 * 1. 调用 ensureExtracted() 确保 Box64 已解压
 * 2. 调用 runBox64() 运行 x86_64 程序
 */
object GlibcBox64Helper {
    
    private const val TAG = "GlibcBox64Helper"
    private const val BOX64_ARCHIVE = "box64_glibc.tar.xz"
    private const val BOX64_DIR = "box64_glibc"
    private const val BOX64_BINARY = "box64"
    private const val VERSION_FILE = ".version"
    
    /**
     * 确保 Box64 glibc 版本已解压
     */
    @JvmStatic
    fun isExtracted(context: Context): Boolean {
        val box64Dir = File(context.filesDir, BOX64_DIR)
        val box64File = File(box64Dir, BOX64_BINARY)
        val versionFile = File(box64Dir, VERSION_FILE)
        
        if (!box64File.exists() || !versionFile.exists()) {
            return false
        }
        
        // 检查版本
        val currentVersion = getAssetVersion(context)
        val extractedVersion = versionFile.readText().trim()
        
        return currentVersion == extractedVersion
    }
    
    /**
     * 获取 Box64 可执行文件路径
     */
    @JvmStatic
    fun getBox64Path(context: Context): String {
        return File(File(context.filesDir, BOX64_DIR), BOX64_BINARY).absolutePath
    }
    
    /**
     * 解压 Box64 glibc 版本
     */
    @JvmStatic
    suspend fun extractBox64(
        context: Context,
        progressCallback: ((Int, String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val box64Dir = File(context.filesDir, BOX64_DIR)
        
        try {
            // 检查 assets 中是否存在
            val assetList = context.assets.list("") ?: emptyArray()
            if (BOX64_ARCHIVE !in assetList) {
                AppLogger.error(TAG, "Box64 glibc archive not found: $BOX64_ARCHIVE")
                return@withContext false
            }
            
            AppLogger.info(TAG, "Extracting Box64 glibc...")
            progressCallback?.invoke(0, "解压 Box64 glibc...")
            
            // 清理旧目录
            if (box64Dir.exists()) {
                box64Dir.deleteRecursively()
            }
            box64Dir.mkdirs()
            
            // 解压 tar.xz
            context.assets.open(BOX64_ARCHIVE).use { assetStream ->
                val bufferedStream = BufferedInputStream(assetStream, 256 * 1024)
                val xzStream = XZInputStream(bufferedStream)
                val tarStream = TarArchiveInputStream(xzStream)
                
                var entry = tarStream.nextEntry
                while (entry != null) {
                    val outputFile = File(box64Dir, entry.name)
                    
                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        FileOutputStream(outputFile).use { fos ->
                            tarStream.copyTo(fos)
                        }
                        
                        // 设置可执行权限
                        if (entry.name == BOX64_BINARY) {
                            outputFile.setExecutable(true, false)
                            outputFile.setReadable(true, false)
                        }
                        
                        progressCallback?.invoke(50, "解压: ${entry.name}")
                        AppLogger.debug(TAG, "Extracted: ${entry.name}")
                    }
                    entry = tarStream.nextEntry
                }
            }
            
            // 验证
            val box64File = File(box64Dir, BOX64_BINARY)
            if (!box64File.exists()) {
                AppLogger.error(TAG, "Box64 binary not found after extraction")
                return@withContext false
            }
            
            // 写入版本标记
            val versionFile = File(box64Dir, VERSION_FILE)
            versionFile.writeText(getAssetVersion(context))
            
            progressCallback?.invoke(100, "解压完成")
            AppLogger.info(TAG, "Box64 glibc extracted: ${box64File.absolutePath}")
            AppLogger.info(TAG, "Size: ${box64File.length() / 1024 / 1024} MB")
            
            true
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to extract Box64 glibc", e)
            progressCallback?.invoke(-1, "解压失败: ${e.message}")
            try {
                box64Dir.deleteRecursively()
            } catch (_: Exception) {}
            false
        }
    }
    
    /**
     * 确保 Box64 已解压
     */
    @JvmStatic
    suspend fun ensureExtracted(context: Context): Boolean {
        if (isExtracted(context)) {
            AppLogger.info(TAG, "Box64 glibc already extracted")
            return true
        }
        return extractBox64(context)
    }
    
    /**
     * 通过 glibc_bridge 运行 Box64
     * 
     * @param context Android Context
     * @param args 参数数组，第一个参数应该是要运行的 x86_64 程序路径
     * @param workDir 工作目录
     * @param envVars 环境变量数组 (格式: "KEY=VALUE")
     * @return 退出码
     */
    @JvmStatic
    fun runBox64(
        context: Context,
        args: Array<String>,
        workDir: String? = null,
        envVars: Array<String>? = null
    ): Int {
        val box64Path = getBox64Path(context)
        val box64File = File(box64Path)
        
        if (!box64File.exists()) {
            AppLogger.error(TAG, "Box64 glibc not found: $box64Path")
            return -1
        }
        
        // 构建完整参数：box64 + 原始参数
        val fullArgs = args  // args 已经包含要运行的程序
        
        // 设置默认环境变量
        val defaultEnvVars = buildDefaultEnvVars(context, workDir)
        val finalEnvVars = if (envVars != null) {
            (defaultEnvVars + envVars.toList()).toTypedArray()
        } else {
            defaultEnvVars.toTypedArray()
        }
        
        val rootfsPath = "${context.filesDir.absolutePath}/rootfs"
        
        AppLogger.info(TAG, "Running Box64 glibc:")
        AppLogger.info(TAG, "  Binary: $box64Path")
        AppLogger.info(TAG, "  Args: ${fullArgs.joinToString(" ")}")
        AppLogger.info(TAG, "  Rootfs: $rootfsPath")
        
        // 确保 glibc_bridge 已加载
        NativeBridge.loadLibrary()
        
        // 通过 glibc_bridge 执行
        return NativeBridge.runWithEnv(
            box64Path,
            fullArgs,
            finalEnvVars,
            rootfsPath
        )
    }
    
    /**
     * 通过 glibc_bridge 运行 Box64（Fork 模式 - 隔离执行）
     * 
     * 使用 fork 在独立进程中运行，避免影响 Android 主进程的其他线程。
     * 适用于 SteamCMD 等可能与 Android 线程产生冲突的程序。
     *
     * @param context Android Context
     * @param args 要传递给 Box64 的参数（包括要运行的程序路径）
     * @param workDir 工作目录
     * @param envVars 额外的环境变量
     * @return 程序退出码
     */
    fun runBox64Forked(
        context: Context,
        args: Array<String>,
        workDir: String? = null,
        envVars: Array<String>? = null
    ): Int {
        val box64Path = getBox64Path(context)
        if (!File(box64Path).exists()) {
            AppLogger.error(TAG, "Box64 not found: $box64Path")
            return -1
        }
        
        // 构建完整参数：box64 + 原始参数
        val fullArgs = args  // args 已经包含要运行的程序
        
        // 设置默认环境变量
        val defaultEnvVars = buildDefaultEnvVars(context, workDir)
        val finalEnvVars = if (envVars != null) {
            (defaultEnvVars + envVars.toList()).toTypedArray()
        } else {
            defaultEnvVars.toTypedArray()
        }
        
        val rootfsPath = "${context.filesDir.absolutePath}/rootfs"
        
        AppLogger.info(TAG, "Running Box64 glibc (FORKED mode):")
        AppLogger.info(TAG, "  Binary: $box64Path")
        AppLogger.info(TAG, "  Args: ${fullArgs.joinToString(" ")}")
        AppLogger.info(TAG, "  Rootfs: $rootfsPath")
        
        // 确保 glibc_bridge 已加载
        NativeBridge.loadLibrary()
        
        // 通过 glibc_bridge fork 模式执行
        return NativeBridge.runForked(
            box64Path,
            fullArgs,
            finalEnvVars,
            rootfsPath
        )
    }
    
    /**
     * 构建默认环境变量
     */
    private fun buildDefaultEnvVars(context: Context, workDir: String?): List<String> {
        val filesDir = context.filesDir.absolutePath
        val rootfsPath = "$filesDir/rootfs"
        val x64libPath = "$filesDir/x64lib"
        
        val envVars = mutableListOf<String>()
        
        // Box64 详细日志配置
        envVars.add("BOX64_LOG=1")                    // 最详细的日志级别
        envVars.add("BOX64_SHOWSEGV=1")               // 显示段错误详情
        envVars.add("BOX64_SHOWBT=1")                 // 显示回溯
        envVars.add("BOX64_DYNAREC_LOG=1")            // Dynarec 日志
        envVars.add("BOX64_DLSYM_ERROR=1")            // 显示 dlsym 错误
        envVars.add("BOX64_DYNAREC=0")                // 暂时禁用 dynarec 以获得更清晰的错误
        envVars.add("BOX64_NOPULSE=1")                // 禁用 PulseAudio
        envVars.add("BOX64_NOGTK=1")                  // 禁用 GTK
        
        // 库搜索路径
        val ldLibraryPath = buildString {
            append("$rootfsPath/usr/lib/x86_64-linux-gnu")
            append(":$x64libPath")
            if (workDir != null) {
                append(":$workDir")
            }
        }
        envVars.add("BOX64_LD_LIBRARY_PATH=$ldLibraryPath")
        
        // 临时目录
        val tmpDir = "$rootfsPath/tmp"
        File(tmpDir).mkdirs()
        envVars.add("TMPDIR=$tmpDir")
        envVars.add("TMP=$tmpDir")
        envVars.add("TEMP=$tmpDir")
        
        // SDL 配置
        envVars.add("SDL_AUDIODRIVER=android")
        envVars.add("SDL_MOUSE_TOUCH_EVENTS=1")
        envVars.add("SDL_TOUCH_MOUSE_EVENTS=1")
        
        // Locale
        envVars.add("LC_ALL=C.UTF-8")
        envVars.add("LANG=C.UTF-8")
        
        return envVars
    }
    
    /**
     * 获取 asset 版本
     */
    private fun getAssetVersion(context: Context): String {
        return try {
            context.assets.openFd(BOX64_ARCHIVE).use { afd ->
                "v1_${afd.length}"
            }
        } catch (e: Exception) {
            try {
                context.assets.open(BOX64_ARCHIVE).use { stream ->
                    var size = 0L
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (stream.read(buffer).also { read = it } != -1) {
                        size += read
                    }
                    "v1_$size"
                }
            } catch (e2: Exception) {
                "unknown"
            }
        }
    }
}
