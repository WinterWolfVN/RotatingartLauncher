package com.app.ralaunch.core.common

import android.content.Context
import com.app.ralaunch.shared.core.model.domain.GameItem
import com.app.ralaunch.feature.patch.data.PatchManager
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.feature.game.legacy.GameActivity
import com.app.ralaunch.core.common.util.AppLogger
import java.io.File

/**
 * 游戏启动管理器
 *
 * 使用新的存储结构: games/{GameDirName}/game_info.json
 * 所有路径都是相对于游戏目录的
 */
class GameLaunchManager(private val context: Context) {

    companion object {
        private const val TAG = "GameLaunchManager"
    }

    fun launchGame(game: GameItem): Boolean {
        android.util.Log.d(TAG, ">>> launchGame called for: ${game.displayedName}")
        AppLogger.info(TAG, "launchGame called for: ${game.displayedName}, path: ${game.gameExePathRelative}")

        // 获取游戏目录和完整路径
        val gameDir = getGameDirectory(game)
        val gameFile = File(gameDir, game.gameExePathRelative)

        if (!gameFile.exists() || !gameFile.isFile) {
            AppLogger.error(TAG, "Assembly file not found: ${gameFile.absolutePath}")
            return false
        }

        AppLogger.info(TAG, "Game runtime: dotnet")

        val patchManager: PatchManager? = try {
            KoinJavaComponent.getOrNull(PatchManager::class.java)
        } catch (e: Exception) { null }
        val gameCategoryId = game.gameId
        val enabledPatches = patchManager?.getApplicableAndEnabledPatches(gameCategoryId, gameFile.toPath()) ?: emptyList()
        AppLogger.info(TAG, "Game: $gameCategoryId, Applicable patches: ${enabledPatches.size}")

        GameActivity.launch(
            context = context,
            gameName = game.displayedName,
            assemblyPath = gameFile.absolutePath,
            gameId = game.gameId,
            gamePath = gameDir.absolutePath,
            rendererOverride = game.rendererOverride,
            enabledPatchIds = enabledPatches
                .takeIf { it.isNotEmpty() }
                ?.let { ArrayList(it.map { patch -> patch.manifest.id }) }
        )

        return true
    }

    /**
     * 获取游戏目录
     * 根据新的存储结构，目录名就是 storageBasePathRelative
     */
    private fun getGameDirectory(game: GameItem): File {
        val gamesDir = File(context.getExternalFilesDir(null), "games")
        return File(gamesDir, game.storageRootPathRelative)
    }

    fun launchAssembly(assemblyFile: File?): Boolean {
        if (assemblyFile == null || !assemblyFile.exists()) {
            AppLogger.error(TAG, "Assembly file is null or does not exist")
            return false
        }

        GameActivity.launch(
            context = context,
            gameName = assemblyFile.name,
            assemblyPath = assemblyFile.absolutePath
        )

        return true
    }
}
