package com.app.ralaunch.manager

import android.content.Context
import com.app.ralaunch.shared.domain.model.GameItem
import com.app.ralaunch.shared.AppConstants
import com.app.ralaunch.utils.AppLogger
import com.app.ralaunch.utils.FileUtils
import java.io.File
import java.nio.file.Paths

/**
 * 游戏删除管理器
 *
 * 使用新的存储结构: games/{GameDirName}/game_info.json
 */
class GameDeletionManager(private val context: Context) {

    fun deleteGameFiles(game: GameItem): Boolean {
        return try {
            val gameDir = getGameDirectory(game) ?: return false

            val dirPath = gameDir.absolutePath
            if (!dirPath.contains("/files/games/") && !dirPath.contains("/files/imported_games/")) {
                return false
            }

            FileUtils.deleteDirectoryRecursively(Paths.get(gameDir.absolutePath))
        } catch (e: Exception) {
            AppLogger.error("GameDeletionManager", "删除游戏文件时发生错误: ${e.message}")
            false
        }
    }

    fun deleteGame(path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        return try {
            FileUtils.deleteDirectoryRecursively(Paths.get(path))
        } catch (e: Exception) {
            AppLogger.error("GameDeletionManager", "删除游戏文件时发生错误: ${e.message}")
            false
        }
    }

    /**
     * 获取游戏目录
     * 根据新的存储结构，目录名就是 storageBasePathRelative
     */
    private fun getGameDirectory(game: GameItem): File? {
        if (game.storageRootPathRelative.isBlank()) return null

        val gamesDir = File(context.getExternalFilesDir(null), AppConstants.Dirs.GAMES)
        return File(gamesDir, game.storageRootPathRelative)
    }
}
