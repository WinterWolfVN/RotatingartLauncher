package com.app.ralaunch.installer

import android.content.Context
import android.util.Log
import com.app.ralaunch.core.AssemblyPatcher
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.data.model.GameItem
import com.app.ralaunch.icon.IconExtractor
import kotlinx.coroutines.Job
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 安装插件基类
 * 提供通用的安装工具方法，减少代码重复
 */
abstract class BaseInstallPlugin : GameInstallPlugin {
    
    protected var installJob: Job? = null
    protected var isCancelled = false
    
    override fun cancel() {
        isCancelled = true
        installJob?.cancel()
    }
    
    /**
     * 从游戏目录提取图标
     * @param outputDir 游戏目录
     * @param definition 游戏定义（用于搜索模式）
     * @return 图标路径，失败返回 null
     */
    protected fun extractIcon(outputDir: File, definition: GameDefinition): String? {
        return try {
            // 1. 检查预设图标
            val presetIcon = findPresetIcon(outputDir, definition)
            if (presetIcon != null) return presetIcon
            
            // 2. 查找图标源文件
            val iconSourceFile = findIconSourceFile(outputDir, definition)
            if (iconSourceFile == null || !iconSourceFile.exists()) return null
            
            // 3. 提取图标
            val iconOutputPath = File(outputDir, "icon.png").absolutePath
            val success = IconExtractor.extractIconToPng(iconSourceFile.absolutePath, iconOutputPath)
            
            if (success && File(iconOutputPath).exists() && File(iconOutputPath).length() > 0) {
                iconOutputPath
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "提取图标失败: ${e.message}")
            null
        }
    }
    
    private fun findPresetIcon(outputDir: File, definition: GameDefinition): String? {
        // 检查常见预设图标名称
        val presetNames = definition.iconPatterns.flatMap { pattern ->
            listOf("$pattern.png", "$pattern.ico", "${pattern}_icon.png")
        } + listOf("icon.png", "game_icon.png")
        
        for (name in presetNames) {
            val iconFile = outputDir.resolve(name)
            if (iconFile.exists()) return iconFile.absolutePath
        }
        return null
    }
    
    private fun findIconSourceFile(outputDir: File, definition: GameDefinition): File? {
        // 1. 先尝试启动目标
        val launchTarget = definition.launchTarget
        val targetFile = File(outputDir, launchTarget)
        
        // 如果是 DLL，尝试找对应的 EXE，如果没有则使用 DLL 本身
        if (launchTarget.lowercase().endsWith(".dll")) {
            val baseName = launchTarget.substringBeforeLast(".")
            val exeFile = File(outputDir, "$baseName.exe")
            if (exeFile.exists() && IconExtractor.hasIcon(exeFile.absolutePath)) {
                return exeFile
            }
            // DLL 文件本身也可能包含图标（PE 格式）
            if (targetFile.exists() && IconExtractor.hasIcon(targetFile.absolutePath)) {
                return targetFile
            }
        }
        
        if (targetFile.exists() && targetFile.extension.lowercase() == "exe") {
            return targetFile
        }
        
        // 2. 搜索匹配模式的 EXE/DLL 文件（PE 格式都可能包含图标）
        val peFiles = outputDir.walkTopDown()
            .filter { it.isFile && (it.extension.lowercase() == "exe" || it.extension.lowercase() == "dll") }
            .toList()
        
        if (peFiles.isEmpty()) return null
        
        // 优先选择名称包含游戏模式的 EXE 文件
        for (pattern in definition.iconPatterns) {
            // 先找 EXE
            val matchedExe = peFiles.find { 
                it.extension.lowercase() == "exe" && 
                it.name.lowercase().contains(pattern.lowercase()) &&
                IconExtractor.hasIcon(it.absolutePath)
            }
            if (matchedExe != null) return matchedExe
            
            // 再找 DLL
            val matchedDll = peFiles.find { 
                it.extension.lowercase() == "dll" && 
                it.name.lowercase().contains(pattern.lowercase()) &&
                IconExtractor.hasIcon(it.absolutePath)
            }
            if (matchedDll != null) return matchedDll
        }
        
        // 3. 返回第一个包含图标的 PE 文件（优先 EXE）
        val exeWithIcon = peFiles.filter { it.extension.lowercase() == "exe" }
            .find { IconExtractor.hasIcon(it.absolutePath) }
        if (exeWithIcon != null) return exeWithIcon
        
        val dllWithIcon = peFiles.filter { it.extension.lowercase() == "dll" }
            .find { IconExtractor.hasIcon(it.absolutePath) }
        if (dllWithIcon != null) return dllWithIcon
        
        // 4. 如果都没有图标，返回第一个 EXE（尝试提取）
        return peFiles.firstOrNull { it.extension.lowercase() == "exe" }
    }
    
    /**
     * 创建游戏信息文件 (game_info.json)
     * @param outputDir 游戏目录
     * @param definition 游戏定义
     * @param iconPath 图标路径
     */
    protected fun createGameInfo(outputDir: File, definition: GameDefinition, iconPath: String?) {
        val infoFile = File(outputDir, "game_info.json")
        val iconField = if (iconPath != null) {
            """,
  "icon_path": "${iconPath.replace("\\", "\\\\")}" """
        } else ""
        
        val rendererField = if (definition.defaultRenderer != null) {
            """,
  "default_renderer": "${definition.defaultRenderer}" """
        } else ""
        
        val json = """
{
  "game_name": "${definition.displayName}",
  "game_type": "${definition.id}",
  "launch_target": "${definition.launchTarget}",
  "runtime": "${definition.runtime}",
  "install_time": "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}"$iconField$rendererField
}
        """.trimIndent()
        infoFile.writeText(json)
    }
    
    /**
     * 安装 MonoMod 库到游戏目录
     * @param gameDir 游戏目录
     * @return 是否成功
     */
    protected fun installMonoMod(gameDir: File): Boolean {
        return try {
            val context: Context = KoinJavaComponent.get(Context::class.java)
            
            // 1. 解压 MonoMod 到目录
            val extractSuccess = AssemblyPatcher.extractMonoMod(context)
            if (!extractSuccess) {
                Log.w(TAG, "MonoMod 解压失败")
                return false
            }
            
            // 2. 应用补丁到游戏目录
            val patchedCount = AssemblyPatcher.applyMonoModPatches(context, gameDir.absolutePath, true)
            
            if (patchedCount >= 0) {
                Log.i(TAG, "MonoMod 已应用，替换了 $patchedCount 个文件")
                true
            } else {
                Log.w(TAG, "MonoMod 应用失败")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "MonoMod 安装异常", e)
            false
        }
    }
    
    /**
     * 复制目录内容
     * @param source 源目录
     * @param target 目标目录
     */
    protected fun copyDirectory(source: File, target: File) {
        if (!target.exists()) {
            target.mkdirs()
        }
        
        source.listFiles()?.forEach { file ->
            val targetFile = File(target, file.name)
            if (file.isDirectory) {
                copyDirectory(file, targetFile)
            } else {
                file.copyTo(targetFile, overwrite = true)
            }
        }
    }
    
    /**
     * 从 GameDefinition 创建 GameItem
     * 
     * @param definition 游戏定义
     * @param gameDir 游戏目录路径（不是程序集文件路径）
     * @param gameBasePath 游戏根目录路径（用于删除）
     * @param iconPath 图标路径
     * @param gameBodyPath 游戏本体路径（可选）
     */
    protected fun createGameItem(
        definition: GameDefinition,
        gameDir: String,
        gameBasePath: String,
        iconPath: String?,
        gameBodyPath: String? = null
    ): GameItem {
        // 将 gameDir + launchTarget 组合成完整的程序集路径
        val assemblyPath = File(gameDir, definition.launchTarget).absolutePath
        Log.i(TAG, "Creating GameItem: gameDir=$gameDir, launchTarget=${definition.launchTarget}, assemblyPath=$assemblyPath")
        
        return GameItem.fromDefinition(
            definition = definition,
            gamePath = assemblyPath,
            gameBasePath = gameBasePath,
            iconPath = iconPath,
            gameBodyPath = gameBodyPath
        )
    }
    
    companion object {
        private const val TAG = "BaseInstallPlugin"
    }
}
