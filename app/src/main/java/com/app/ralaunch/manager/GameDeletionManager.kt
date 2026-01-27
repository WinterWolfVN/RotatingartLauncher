package com.app.ralaunch.manager

import android.content.Context
import com.app.ralaunch.data.model.GameItem
import com.app.ralaunch.shared.AppConstants
import com.app.ralaunch.utils.AppLogger
import com.app.ralaunch.utils.FileUtils
import java.io.File
import java.nio.file.Paths

/**
 * 游戏删除管理器
 */
class GameDeletionManager(private val context: Context) {

    fun deleteGameFiles(game: GameItem): Boolean {
        return try {
            if (game.isShortcut) return false

            val gameDir = findGameDirectory(game) ?: return false

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

    private fun findGameDirectory(game: GameItem): File? {
        val gameBasePath = game.gameBasePath
        if (!gameBasePath.isNullOrEmpty()) {
            return File(gameBasePath)
        }

        val gamePath = game.gamePath
        if (gamePath.isNullOrEmpty()) return null

        val gameFile = File(gamePath)
        var gameDir: File? = null
        var parent = gameFile.parentFile

        while (parent != null && parent.name != AppConstants.Dirs.GAMES) {
            gameDir = parent
            parent = parent.parentFile
        }

        return gameDir ?: gameFile.parentFile
    }
}
