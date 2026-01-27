package com.app.ralaunch.manager

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.app.ralaunch.data.model.GameItem
import com.app.ralaunch.patch.PatchManager
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.ui.game.GameActivity
import com.app.ralaunch.utils.AppLogger
import org.json.JSONObject
import java.io.File
import java.io.FileReader

/**
 * 游戏启动管理器
 */
class GameLaunchManager(private val context: Context) {

    companion object {
        private const val TAG = "GameLaunchManager"
    }

    fun launchGame(game: GameItem): Boolean {
        android.util.Log.d(TAG, ">>> launchGame called for: ${game.gameName}")
        AppLogger.info(TAG, "launchGame called for: ${game.gameName}, path: ${game.gamePath}")
        
        var assemblyPath = game.gamePath
        var assemblyFile = File(assemblyPath)

        // 如果 gamePath 是目录而不是文件，尝试从 game_info.json 获取 launch_target
        if (assemblyFile.exists() && assemblyFile.isDirectory) {
            AppLogger.info(TAG, "gamePath is a directory, attempting to resolve launch_target...")
            val resolvedPath = resolveLaunchTargetFromDir(assemblyFile)
            if (resolvedPath != null) {
                assemblyPath = resolvedPath
                assemblyFile = File(assemblyPath)
                AppLogger.info(TAG, "Resolved assembly path: $assemblyPath")
            }
        }

        if (!assemblyFile.exists() || !assemblyFile.isFile) {
            AppLogger.error(TAG, "Assembly file not found: $assemblyPath")
            return false
        }

        val runtime = detectRuntime(assemblyFile)
        AppLogger.info(TAG, "Game runtime: $runtime")

        val patchManager: PatchManager? = try {
            KoinJavaComponent.getOrNull(PatchManager::class.java)
        } catch (e: Exception) { null }
        val gameId = game.gameName
        val enabledPatches = patchManager?.getApplicableAndEnabledPatches(gameId, assemblyFile.toPath()) ?: emptyList()
        AppLogger.info(TAG, "Game: $gameId, Applicable patches: ${enabledPatches.size}")

        // 检测游戏特定的渲染器设置
        val defaultRenderer = detectDefaultRenderer(assemblyFile)
        if (defaultRenderer != null) {
            AppLogger.info(TAG, "Game default renderer: $defaultRenderer")
        }

        val intent = Intent(context, GameActivity::class.java).apply {
            putExtra("GAME_NAME", game.gameName)
            putExtra("ASSEMBLY_PATH", assemblyPath)
            putExtra("GAME_ID", game.gamePath)
            putExtra("GAME_PATH", game.gamePath)
            putExtra("RUNTIME", runtime)
            putExtra("DEFAULT_RENDERER", defaultRenderer)

            if (runtime != "box64" && enabledPatches.isNotEmpty()) {
                putStringArrayListExtra(
                    "ENABLED_PATCH_IDS",
                    ArrayList(enabledPatches.map { it.manifest.id })
                )
            }
        }

        context.startActivity(intent)
        (context as? Activity)?.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

        return true
    }

    /**
     * 从游戏目录解析启动目标
     * 1. 首先尝试从 game_info.json 读取 launch_target
     * 2. 如果没有，尝试查找常见的启动文件
     */
    private fun resolveLaunchTargetFromDir(gameDir: File): String? {
        try {
           
            val gameInfoFile = File(gameDir, "game_info.json")
            if (gameInfoFile.exists()) {
                val content = FileReader(gameInfoFile).use { it.readText() }
                val json = JSONObject(content)
                if (json.has("launch_target")) {
                    val launchTarget = json.getString("launch_target")
                    val targetFile = File(gameDir, launchTarget)
                    if (targetFile.exists() && targetFile.isFile) {
                        return targetFile.absolutePath
                    }
                }
            }
            
         
            
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Failed to resolve launch target: ${e.message}")
        }
        return null
    }

    private fun detectRuntime(assemblyFile: File): String {
        try {
            val json = readGameInfoJson(assemblyFile)
            if (json != null) {
                if (json.has("runtime")) {
                    return json.getString("runtime")
                }

                if (json.has("game_type")) {
                    val gameType = json.getString("game_type")
                    if (gameType == "starbound") {
                        return "box64"
                    }
                }
            }

            val fileName = assemblyFile.name.lowercase()
            return when {
                fileName.endsWith(".dll") || fileName.endsWith(".exe") -> "dotnet"
                !fileName.contains(".") -> "box64"
                else -> "dotnet"
            }
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Failed to detect runtime: ${e.message}")
        }
        return "dotnet"
    }
    
    /**
     * 从 game_info.json 检测默认渲染器
     */
    private fun detectDefaultRenderer(assemblyFile: File): String? {
        return try {
            val json = readGameInfoJson(assemblyFile)
            json?.optString("default_renderer", null)?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Failed to detect default renderer: ${e.message}")
            null
        }
    }
    
    /**
     * 读取 game_info.json 文件
     */
    private fun readGameInfoJson(assemblyFile: File): JSONObject? {
        val gameDir = assemblyFile.parentFile ?: return null
        var gameInfoFile = File(gameDir, "game_info.json")

        if (!gameInfoFile.exists()) {
            gameInfoFile = File(gameDir.parentFile, "game_info.json")
        }

        return if (gameInfoFile.exists()) {
            val content = FileReader(gameInfoFile).use { it.readText() }
            JSONObject(content)
        } else null
    }

    fun launchAssembly(assemblyFile: File?): Boolean {
        if (assemblyFile == null || !assemblyFile.exists()) {
            AppLogger.error(TAG, "Assembly file is null or does not exist")
            return false
        }

        val intent = Intent(context, GameActivity::class.java).apply {
            putExtra("ASSEMBLY_PATH", assemblyFile.absolutePath)
            putExtra("GAME_NAME", assemblyFile.name)
        }

        context.startActivity(intent)
        (context as? Activity)?.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

        return true
    }
}
