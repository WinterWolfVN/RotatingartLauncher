package com.app.ralaunch.steam

import android.content.Context
import com.app.ralaunch.box64.GlibcBox64Helper
import com.app.ralaunch.box64.NativeBridge
import com.app.ralaunch.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream

/**
 * SteamCMD 管理器
 * 
 * 负责解压和运行 SteamCMD，用于下载 Steam 创意工坊内容。
 * SteamCMD 已预打包在 assets 中，首次使用时自动解压。
 * 通过 glibc_bridge + Box64 运行 Linux 版 SteamCMD。
 * 
 * TModLoader AppID: 1281930
 */
object SteamCmdManager {
    
    private const val TAG = "SteamCmdManager"
    
    // SteamCMD assets 文件名 (AAPT 会去掉 .gz 扩展名)
    private const val STEAMCMD_ASSET = "steamcmd_linux.tar"
    private const val STEAMCMD_ASSET_GZ = "steamcmd_linux.tar.gz"
    
    // SteamCMD 目录名
    private const val STEAMCMD_DIR = "steamcmd"
    private const val STEAMCMD_SCRIPT = "steamcmd.sh"
    private const val STEAMCMD_BINARY = "linux32/steamcmd"
    private const val VERSION_FILE = ".version"
    
    // TModLoader AppID
    const val TMODLOADER_APP_ID = "1281930"
    
    /**
     * 获取 SteamCMD 目录
     */
    fun getSteamCmdDir(context: Context): File {
        return File(context.filesDir, STEAMCMD_DIR)
    }
    
    /**
     * 获取 SteamCMD 脚本路径
     */
    fun getSteamCmdPath(context: Context): String {
        return File(getSteamCmdDir(context), STEAMCMD_SCRIPT).absolutePath
    }
    
    /**
     * 检查 SteamCMD 是否已安装且版本正确
     */
    fun isInstalled(context: Context): Boolean {
        val steamcmdDir = getSteamCmdDir(context)
        val steamcmdScript = File(steamcmdDir, STEAMCMD_SCRIPT)
        if (!steamcmdScript.exists() || !steamcmdScript.canExecute()) {
            return false
        }
        // 检查版本
        return !needsUpdate(context)
    }
    
    /**
     * 检查是否需要更新 SteamCMD
     */
    private fun needsUpdate(context: Context): Boolean {
        val versionFile = File(getSteamCmdDir(context), VERSION_FILE)
        if (!versionFile.exists()) return true
        
        val currentVersion = getAssetVersion(context)
        val installedVersion = versionFile.readText().trim()
        return currentVersion != installedVersion
    }
    
    /**
     * 获取 assets 中 SteamCMD 的版本（基于文件大小）
     */
    private fun getAssetVersion(context: Context): String {
        return try {
            // 先尝试 .tar，再尝试 .tar.gz
            val assetName = getActualAssetName(context)
            context.assets.openFd(assetName).use { afd ->
                "v1_${afd.length}"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    /**
     * 获取实际的 asset 文件名（AAPT 可能去掉 .gz 扩展名）
     */
    private fun getActualAssetName(context: Context): String {
        return try {
            context.assets.openFd(STEAMCMD_ASSET).close()
            STEAMCMD_ASSET
        } catch (e: Exception) {
            try {
                context.assets.openFd(STEAMCMD_ASSET_GZ).close()
                STEAMCMD_ASSET_GZ
            } catch (e2: Exception) {
                STEAMCMD_ASSET // 默认
            }
        }
    }
    
    /**
     * 从 assets 解压安装 SteamCMD
     * 
     * @param context Android Context
     * @param onProgress 进度回调 (0-100, message)
     * @return 是否成功
     */
    suspend fun installSteamCmd(
        context: Context,
        onProgress: ((Int, String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val steamcmdDir = getSteamCmdDir(context)
        
        try {
            onProgress?.invoke(0, "准备解压 SteamCMD...")
            AppLogger.info(TAG, "开始从 assets 解压 SteamCMD...")
            
            // 创建目录
            if (steamcmdDir.exists()) {
                steamcmdDir.deleteRecursively()
            }
            steamcmdDir.mkdirs()
            
            // 从 assets 解压 tar 或 tar.gz
            onProgress?.invoke(20, "解压 SteamCMD...")
            
            val assetName = getActualAssetName(context)
            val isGzipped = assetName.endsWith(".gz")
            AppLogger.info(TAG, "使用 asset 文件: $assetName, isGzipped: $isGzipped")
            
            context.assets.open(assetName).use { assetStream ->
                val bufferedStream = BufferedInputStream(assetStream, 1024 * 1024)
                
                // 根据文件类型选择是否需要 GZIP 解压
                val tarInputStream = if (isGzipped) {
                    org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
                        java.util.zip.GZIPInputStream(bufferedStream)
                    )
                } else {
                    org.apache.commons.compress.archivers.tar.TarArchiveInputStream(bufferedStream)
                }
                
                tarInputStream.use { tar ->
                    var entry = tar.nextTarEntry
                    var count = 0
                    while (entry != null) {
                        val file = File(steamcmdDir, entry.name)
                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            FileOutputStream(file).use { output ->
                                tar.copyTo(output)
                            }
                            // 保留可执行权限
                            val mode = entry.mode
                            if ((mode and 64) != 0 || (mode and 8) != 0 || (mode and 1) != 0) {
                                file.setExecutable(true, false)
                            }
                        }
                        count++
                        // 更新进度
                        val progress = 20 + (count * 60 / 20).coerceAtMost(60) // 假设约20个文件
                        onProgress?.invoke(progress, "解压中: ${entry.name}")
                        entry = tar.nextTarEntry
                    }
                }
            }
            
            AppLogger.info(TAG, "SteamCMD 解压完成")
            
            // 确保可执行权限
            onProgress?.invoke(90, "设置权限...")
            File(steamcmdDir, STEAMCMD_SCRIPT).setExecutable(true, false)
            File(steamcmdDir, STEAMCMD_BINARY).setExecutable(true, false)
            
            // 写入版本文件
            File(steamcmdDir, VERSION_FILE).writeText(getAssetVersion(context))
            
            onProgress?.invoke(100, "安装完成")
            AppLogger.info(TAG, "SteamCMD 安装成功")
            
            true
        } catch (e: Exception) {
            AppLogger.error(TAG, "SteamCMD 安装失败", e)
            onProgress?.invoke(-1, "安装失败: ${e.message}")
            
            // 清理
            try {
                steamcmdDir.deleteRecursively()
            } catch (_: Exception) {}
            
            false
        }
    }
    
    /**
     * 确保 SteamCMD 已安装
     */
    suspend fun ensureSteamCmd(
        context: Context,
        onProgress: ((Int, String) -> Unit)? = null
    ): Boolean {
        if (isInstalled(context)) {
            AppLogger.info(TAG, "SteamCMD 已安装")
            return true
        }
        return installSteamCmd(context, onProgress)
    }
    
    /**
     * 下载创意工坊物品
     * 
     * @param context Android Context
     * @param appId Steam AppID (例如 TModLoader: 1281930)
     * @param workshopId 创意工坊物品 ID
     * @param onOutput 输出回调
     * @return 下载的文件目录
     */
    suspend fun downloadWorkshopItem(
        context: Context,
        appId: String,
        workshopId: String,
        onOutput: ((String) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // 确保 SteamCMD 已安装
            if (!isInstalled(context)) {
                return@withContext Result.failure(Exception("SteamCMD 未安装"))
            }
            
            // 确保 Box64 glibc 已解压
            if (!GlibcBox64Helper.isExtracted(context)) {
                onOutput?.invoke("正在解压 Box64...")
                val extracted = GlibcBox64Helper.extractBox64(context)
                if (!extracted) {
                    return@withContext Result.failure(Exception("Box64 解压失败"))
                }
            }
            
            // 确保 32 位 x86 库已解压 (BOX32 需要)
            onOutput?.invoke("检查 32 位库...")
            if (!ensureLib32Extracted(context)) {
                return@withContext Result.failure(Exception("32 位库解压失败"))
            }
            
            // 确保 glibc_bridge 已初始化
            NativeBridge.loadLibrary()
            val rootfsPath = "${context.filesDir.absolutePath}/rootfs"
            if (!File(rootfsPath).exists()) {
                NativeBridge.init(context, context.filesDir.absolutePath)
            }
            
            val steamcmdDir = getSteamCmdDir(context)
            val workshopDir = File(steamcmdDir, "steamapps/workshop/content/$appId/$workshopId")
            
            onOutput?.invoke("启动 SteamCMD...")
            onOutput?.invoke("AppID: $appId")
            onOutput?.invoke("Workshop ID: $workshopId")
            
            // 直接运行 steamcmd 可执行文件（linux32/steamcmd），不需要 bash
            // SteamCMD 的 linux32/steamcmd 是一个 32 位 ELF 可执行文件
            val steamcmdExe = File(steamcmdDir, "linux32/steamcmd")
            if (!steamcmdExe.exists()) {
                return@withContext Result.failure(Exception("steamcmd 可执行文件不存在: ${steamcmdExe.absolutePath}"))
            }
            
            // 构建 SteamCMD 参数
            val steamcmdArgs = arrayOf(
                "+@sSteamCmdForcePlatformType", "linux",
                "+force_install_dir", steamcmdDir.absolutePath,
                "+login", "anonymous",
                "+workshop_download_item", appId, workshopId,
                "+quit"
            )
            
            // 构建环境变量
            val envVars = buildSteamCmdEnvVars(context, steamcmdDir)
            
            onOutput?.invoke("执行: box64 ${steamcmdExe.absolutePath} ${steamcmdArgs.joinToString(" ")}")
            onOutput?.invoke("---")
            
            // 通过 glibc_bridge + Box64 直接运行 steamcmd 可执行文件 (FORK 模式)
            // 使用 fork 模式隔离执行，避免 pthread_mutex 冲突
            val result = GlibcBox64Helper.runBox64Forked(
                context = context,
                args = arrayOf(steamcmdExe.absolutePath) + steamcmdArgs,
                workDir = steamcmdDir.absolutePath,
                envVars = envVars
            )
            
            onOutput?.invoke("---")
            onOutput?.invoke("SteamCMD 退出码: $result")
            
            // 检查下载结果
            if (workshopDir.exists() && workshopDir.listFiles()?.isNotEmpty() == true) {
                onOutput?.invoke("下载成功: ${workshopDir.absolutePath}")
                Result.success(workshopDir)
            } else if (result == 0) {
                // SteamCMD 成功但文件不存在，可能是无效的 workshop ID
                Result.failure(Exception("下载完成但未找到文件，请检查 Workshop ID 是否正确"))
            } else {
                Result.failure(Exception("SteamCMD 执行失败，退出码: $result"))
            }
            
        } catch (e: Exception) {
            AppLogger.error(TAG, "下载创意工坊物品失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 确保 32 位 x86 库已解压（BOX32 需要）
     */
    private suspend fun ensureLib32Extracted(context: Context): Boolean = withContext(Dispatchers.IO) {
        val lib32Dir = File(context.filesDir, "rootfs/lib/i386-linux-gnu")
        val versionFile = File(lib32Dir, ".version")
        val currentVersion = "v1_lib32"
        
        // 检查是否已解压
        if (lib32Dir.exists() && versionFile.exists()) {
            val installedVersion = versionFile.readText().trim()
            if (installedVersion == currentVersion) {
                AppLogger.info(TAG, "32 位库已存在")
                return@withContext true
            }
        }
        
        try {
            AppLogger.info(TAG, "解压 32 位 x86 库...")
            
            // 创建目录
            lib32Dir.mkdirs()
            
            // 解压 lib32.tar.xz
            context.assets.open("lib32.tar.xz").use { input ->
                org.tukaani.xz.XZInputStream(input).use { xzInput ->
                    org.apache.commons.compress.archivers.tar.TarArchiveInputStream(xzInput).use { tarInput ->
                        var entry = tarInput.nextTarEntry
                        while (entry != null) {
                            val outFile = if (entry.name.startsWith("lib32/")) {
                                File(lib32Dir, entry.name.removePrefix("lib32/"))
                            } else {
                                File(lib32Dir, entry.name)
                            }
                            
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else if (entry.name.endsWith(".so") || entry.name.contains(".so.")) {
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { output ->
                                    tarInput.copyTo(output)
                                }
                                outFile.setExecutable(true, false)
                            }
                            entry = tarInput.nextTarEntry
                        }
                    }
                }
            }
            
            // 写入版本文件
            versionFile.writeText(currentVersion)
            
            AppLogger.info(TAG, "32 位库解压完成: ${lib32Dir.absolutePath}")
            true
        } catch (e: Exception) {
            AppLogger.error(TAG, "解压 32 位库失败", e)
            false
        }
    }
    
    /**
     * 构建 SteamCMD 环境变量
     */
    private fun buildSteamCmdEnvVars(context: Context, steamcmdDir: File): Array<String> {
        val filesDir = context.filesDir.absolutePath
        val rootfsPath = "$filesDir/rootfs"
        val x64libPath = "$filesDir/x64lib"
        val lib32Path = "$rootfsPath/lib/i386-linux-gnu"  // 32 位库路径
        val box64Path = "$filesDir/box64_glibc/box64"     // Box64 可执行文件路径
        
        return arrayOf(
            // ===== 关键：告诉 BOX32 在哪里找到 box64 =====
            "BOX64_BOX32=$box64Path",           // BOX32 relaunch 时使用的路径
            "BOX64_PATH=$filesDir/box64_glibc", // Box64 搜索路径
            "BOX32_BOX32=$box64Path",           // 备用
            
            // Box64 日志配置
            "BOX64_LOG=2",
            "BOX64_SHOWSEGV=1",
            "BOX64_SHOWBT=1",
            "BOX64_DLSYM_ERROR=1",
            "BOX64_DYNAREC=0",
            "BOX64_NOPULSE=1",
            "BOX64_NOGTK=1",
            "BOX64_ALLOWMISSINGLIBS=1",
            
            // BOX32 配置
            "BOX32_LOG=2",
            "BOX32_SHOWSEGV=1",
            "BOX32_SHOWBT=1",
            "BOX32_ALLOWMISSINGLIBS=1",
            "BOX32_DYNAREC=0",
            
            // 32 位库路径
            "BOX32_LD_LIBRARY_PATH=$lib32Path:${steamcmdDir.absolutePath}/linux32",
            
            // 64 位库路径
            "BOX64_LD_LIBRARY_PATH=$rootfsPath/usr/lib/x86_64-linux-gnu:$x64libPath:${steamcmdDir.absolutePath}/linux64",
            
            // 同时设置 BOX86 变量（兼容性）
            "BOX86_LD_LIBRARY_PATH=$lib32Path:${steamcmdDir.absolutePath}/linux32",
            "BOX86_LOG=2",
            "BOX86_ALLOWMISSINGLIBS=1",
            
            // LD_LIBRARY_PATH
            "LD_LIBRARY_PATH=$lib32Path:$rootfsPath/usr/lib/x86_64-linux-gnu:$x64libPath",
            
            // PATH - 包含 box64 目录
            "PATH=$filesDir/box64_glibc:$rootfsPath/bin:$rootfsPath/usr/bin:${steamcmdDir.absolutePath}",
            
            // SteamCMD 配置
            "HOME=${steamcmdDir.absolutePath}",
            "USER=root",
            "STEAMCMD_DIR=${steamcmdDir.absolutePath}",
            
            // 临时目录
            "TMPDIR=$rootfsPath/tmp",
            "TMP=$rootfsPath/tmp",
            "TEMP=$rootfsPath/tmp",
            
            // Locale
            "LC_ALL=C.UTF-8",
            "LANG=C.UTF-8",
            "LANGUAGE=C.UTF-8"
        )
    }
    
    /**
     * 获取已下载的创意工坊物品列表
     */
    fun getDownloadedItems(context: Context, appId: String): List<File> {
        val workshopDir = File(getSteamCmdDir(context), "steamapps/workshop/content/$appId")
        if (!workshopDir.exists()) return emptyList()
        
        return workshopDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
    
}
