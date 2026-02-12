package com.app.ralaunch.manager

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.app.ralaunch.shared.domain.model.GameItem
import com.app.ralaunch.patch.PatchManager
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.ui.game.GameActivity
import com.app.ralaunch.utils.AppLogger
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

        val intent = Intent(context, GameActivity::class.java).apply {
            putExtra("GAME_NAME", game.displayedName)
            putExtra("ASSEMBLY_PATH", gameFile.absolutePath)
            putExtra("GAME_ID", game.gameId)
            putExtra("GAME_PATH", gameDir.absolutePath)

            if (enabledPatches.isNotEmpty()) {
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

        val intent = Intent(context, GameActivity::class.java).apply {
            putExtra("ASSEMBLY_PATH", assemblyFile.absolutePath)
            putExtra("GAME_NAME", assemblyFile.name)
        }

        context.startActivity(intent)
        (context as? Activity)?.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

        return true
    }
}
