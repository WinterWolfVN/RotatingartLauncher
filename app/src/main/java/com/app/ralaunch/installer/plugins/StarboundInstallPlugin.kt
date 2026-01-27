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
 * Starbound 安装插件
 * 支持导入 GOG Linux 版 .sh 安装文件
 */
class StarboundInstallPlugin : BaseInstallPlugin() {
    
    override val pluginId = "starbound"
    override val displayName = "Starbound"
    override val supportedGames = listOf(GameDefinition.STARBOUND)
    
    override fun detectGame(gameFile: File): GameDetectResult? {
        val fileName = gameFile.name.lowercase()
        
        if (fileName.endsWith(".sh") && fileName.contains("starbound")) {
            return GameDetectResult(GameDefinition.STARBOUND)
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
                    callback.onProgress("开始安装 Starbound...", 0)
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
                
                val starboundGameDir = when (result) {
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
                
                val linuxDir = File(starboundGameDir, "linux")
                if (linuxDir.exists()) setExecutablePermissions(linuxDir)
                
                val launchTarget = findStarboundExecutable(starboundGameDir)
                    ?.relativeTo(starboundGameDir)?.path
                    ?: "linux/starbound"
                
                // 初始化 Box64 环境
                withContext(Dispatchers.Main) {
                    callback.onProgress("初始化 Box64 环境...", 90)
                }
                GameLauncher.initializeBox64(KoinJavaComponent.get(Context::class.java))
                
                // 提取图标
                val iconPath = extractGogIcon(starboundGameDir)
                
                // 创建游戏信息文件
                withContext(Dispatchers.Main) {
                    callback.onProgress("完成安装...", 98)
                }
                
                // 创建动态的 GameDefinition（因为 launchTarget 可能变化）
                val dynamicDef = GameDefinition.STARBOUND.copy(launchTarget = launchTarget)
                createGameInfo(starboundGameDir, dynamicDef, iconPath)
                
                val gameItem = createGameItem(
                    definition = dynamicDef,
                    gameDir = starboundGameDir.absolutePath,
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
    
    private fun findStarboundExecutable(gameDir: File): File? {
        val linuxDir = File(gameDir, "linux")
        if (linuxDir.exists()) {
            val exe = File(linuxDir, "starbound")
            if (exe.exists()) return exe
        }
        
        return gameDir.walkTopDown()
            .filter { it.isFile && it.name == "starbound" && !it.name.contains(".") }
            .firstOrNull()
    }
    
    private fun extractGogIcon(gameDir: File): String? {
        val gogIcon = File(gameDir, "support/icon.png")
        if (gogIcon.exists() && gogIcon.length() > 0) return gogIcon.absolutePath
        
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
