package com.app.ralaunch.installer.plugins

import android.content.Context
import com.app.ralaunch.core.GameLauncher
import com.app.ralaunch.installer.*
import org.koin.java.KoinJavaComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Don't Starve (饥荒) 安装插件
 * 支持导入 GOG Linux 版 .sh 安装文件
 */
class DontStarveInstallPlugin : BaseInstallPlugin() {
    
    override val pluginId = "dontstarve"
    override val displayName = "Don't Starve"
    override val supportedGames = listOf(GameDefinition.DONT_STARVE, GameDefinition.DONT_STARVE_TOGETHER)
    
    override fun detectGame(gameFile: File): GameDetectResult? {
        val fileName = gameFile.name.lowercase()
        
        if (fileName.endsWith(".sh") && 
            (fileName.contains("don_t_starve") || fileName.contains("dontstarve") || fileName.contains("dont_starve"))) {
            return GameDetectResult(GameDefinition.DONT_STARVE)
        }
        
        return null
    }
    
    override fun detectModLoader(modLoaderFile: File): ModLoaderDetectResult? = null
    
    override fun install(
        gameFile: File,
        modLoaderFile: File?,
        outputDir: File,
        callback: InstallCallback
    ) {
        isCancelled = false
        
        installJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    callback.onProgress("开始安装 Don't Starve...", 0)
                }
                
                if (!outputDir.exists()) outputDir.mkdirs()
                
                // 解压 GOG .sh 文件
                val result = GameExtractorUtils.extractGogSh(gameFile, outputDir) { msg, progress ->
                    if (!isCancelled) {
                        val progressInt = (progress * 80).toInt().coerceIn(0, 80)
                        CoroutineScope(Dispatchers.Main).launch {
                            callback.onProgress(msg, progressInt)
                        }
                    }
                }
                
                val dontStarveGameDir = when (result) {
                    is GameExtractorUtils.ExtractResult.Error -> {
                        withContext(Dispatchers.Main) { callback.onError(result.message) }
                        return@launch
                    }
                    is GameExtractorUtils.ExtractResult.Success -> result.outputDir
                }
                
                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }
                
                // 查找可执行文件
                withContext(Dispatchers.Main) {
                    callback.onProgress("查找游戏文件...", 85)
                }
                
                val launchTarget = findDontStarveExecutable(dontStarveGameDir)
                if (launchTarget == null) {
                    withContext(Dispatchers.Main) {
                        callback.onError("未找到 Don't Starve 可执行文件")
                    }
                    return@launch
                }
                
                // 设置可执行权限
                val exeFile = File(dontStarveGameDir, launchTarget)
                if (exeFile.exists()) {
                    setExecutablePermissions(exeFile.parentFile)
                }
                
                // 初始化 Box64 环境
                withContext(Dispatchers.Main) {
                    callback.onProgress("初始化 Box64 环境...", 90)
                }
                GameLauncher.initializeBox64(KoinJavaComponent.get(Context::class.java))
                
                // 提取图标
                val iconPath = extractGogIcon(dontStarveGameDir)
                
                // 创建游戏信息文件
                withContext(Dispatchers.Main) {
                    callback.onProgress("完成安装...", 98)
                }
                
                val dynamicDef = GameDefinition.DONT_STARVE.copy(launchTarget = launchTarget)
                createGameInfo(dontStarveGameDir, dynamicDef, iconPath)
                
                val gameItem = createGameItem(
                    definition = dynamicDef,
                    gameDir = dontStarveGameDir.absolutePath,
                    gameBasePath = outputDir.absolutePath,
                    iconPath = iconPath
                )
                
                withContext(Dispatchers.Main) {
                    callback.onProgress("安装完成!", 100)
                    callback.onComplete(gameItem)
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError(e.message ?: "安装失败")
                }
            }
        }
    }
    
    private fun findDontStarveExecutable(gameDir: File): String? {
        val possibleExeNames = listOf(
            "dontstarve", "dontstarve.bin.x86_64", "dontstarve.bin.x86",
            "dontstarve_steam", "dontstarve.x86_64", "dontstarve.x86"
        )
        
        val possibleDirs = listOf(
            "bin64", "bin", "game/bin64", "game/bin", "data/bin64", "data/bin", ""
        )
        
        for (dir in possibleDirs) {
            for (exeName in possibleExeNames) {
                val targetDir = if (dir.isEmpty()) gameDir else File(gameDir, dir)
                val exeFile = File(targetDir, exeName)
                if (exeFile.exists() && exeFile.isFile) {
                    return exeFile.relativeTo(gameDir).path.replace("\\", "/")
                }
            }
        }
        
        return gameDir.walkTopDown()
            .filter { file -> file.isFile && possibleExeNames.any { file.name.equals(it, ignoreCase = true) } }
            .firstOrNull()
            ?.relativeTo(gameDir)
            ?.path
            ?.replace("\\", "/")
    }
    
    private fun extractGogIcon(gameDir: File): String? {
        val iconPaths = listOf("support/icon.png", "data/icon.png", "icon.png", "game/icon.png")
        
        for (path in iconPaths) {
            val icon = File(gameDir, path)
            if (icon.exists() && icon.length() > 0) return icon.absolutePath
        }
        
        return gameDir.walkTopDown()
            .filter { it.isFile && it.name.equals("icon.png", ignoreCase = true) }
            .firstOrNull()
            ?.takeIf { it.length() > 0 }
            ?.absolutePath
    }
    
    private fun setExecutablePermissions(dir: File) {
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                file.setExecutable(true, false)
                file.setReadable(true, false)
            }
        }
    }
}
