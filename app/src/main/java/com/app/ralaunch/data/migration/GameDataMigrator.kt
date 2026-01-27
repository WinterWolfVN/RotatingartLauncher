package com.app.ralaunch.data.migration

import android.content.Context
import android.util.Log
import com.app.ralaunch.shared.data.mapper.GameItemMapper
import com.app.ralaunch.shared.domain.model.GameItem
import com.app.ralaunch.shared.domain.repository.GameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/**
 * 游戏数据迁移器
 *
 * 将旧的 game_list.json 数据迁移到新格式
 */
class GameDataMigrator(
    private val context: Context,
    private val gameRepository: GameRepository
) {
    companion object {
        private const val TAG = "GameDataMigrator"
        private const val LEGACY_GAMES_DIR = "games"
        private const val LEGACY_GAME_LIST_FILE = "game_list.json"
        private const val MIGRATION_COMPLETED_KEY = "games_migrated_v2"
    }

    /**
     * 检查是否需要迁移
     */
    fun needsMigration(): Boolean {
        val prefs = context.getSharedPreferences("migration_prefs", Context.MODE_PRIVATE)
        val migrated = prefs.getBoolean(MIGRATION_COMPLETED_KEY, false)
        if (migrated) return false

        // 检查旧游戏列表文件是否存在
        val gamesDir = File(context.getExternalFilesDir(null), LEGACY_GAMES_DIR)
        val legacyFile = File(gamesDir, LEGACY_GAME_LIST_FILE)
        return legacyFile.exists()
    }

    /**
     * 执行迁移
     */
    suspend fun migrate(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val gamesDir = File(context.getExternalFilesDir(null), LEGACY_GAMES_DIR)
            val legacyFile = File(gamesDir, LEGACY_GAME_LIST_FILE)

            if (!legacyFile.exists()) {
                markMigrationCompleted()
                return@withContext Result.success(Unit)
            }

            // 读取旧数据
            val jsonString = legacyFile.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(jsonString)

            // 转换为新格式
            val games = mutableListOf<GameItem>()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val gameMap = mutableMapOf<String, Any?>()

                jsonObject.keys().forEach { key ->
                    gameMap[key] = jsonObject.opt(key)
                }

                val gameItem = GameItemMapper.fromLegacyMap(gameMap)
                games.add(gameItem)
            }

            // 保存到新存储
            gameRepository.saveGameList(games)

            // 标记迁移完成
            markMigrationCompleted()

            Log.i(TAG, "Game data migration completed: ${games.size} games migrated")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Game data migration failed", e)
            Result.failure(e)
        }
    }

    /**
     * 读取旧游戏列表（不执行迁移）
     */
    suspend fun readLegacyGames(): List<GameItem>? = withContext(Dispatchers.IO) {
        try {
            val gamesDir = File(context.getExternalFilesDir(null), LEGACY_GAMES_DIR)
            val legacyFile = File(gamesDir, LEGACY_GAME_LIST_FILE)

            if (!legacyFile.exists()) return@withContext null

            val jsonString = legacyFile.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(jsonString)

            val games = mutableListOf<GameItem>()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val gameMap = mutableMapOf<String, Any?>()

                jsonObject.keys().forEach { key ->
                    gameMap[key] = jsonObject.opt(key)
                }

                games.add(GameItemMapper.fromLegacyMap(gameMap))
            }

            games
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read legacy games", e)
            null
        }
    }

    private fun markMigrationCompleted() {
        context.getSharedPreferences("migration_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(MIGRATION_COMPLETED_KEY, true)
            .apply()
    }

    /**
     * 重置迁移状态（用于调试）
     */
    fun resetMigrationStatus() {
        context.getSharedPreferences("migration_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(MIGRATION_COMPLETED_KEY, false)
            .apply()
    }
}
