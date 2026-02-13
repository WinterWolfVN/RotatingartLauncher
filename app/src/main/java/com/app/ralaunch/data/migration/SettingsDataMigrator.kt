package com.app.ralaunch.data.migration

import android.content.Context
import android.util.Log
import com.app.ralaunch.shared.data.mapper.SettingsMapper
import com.app.ralaunch.shared.domain.model.AppSettings
import com.app.ralaunch.shared.domain.repository.SettingsRepositoryV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * 设置数据迁移器
 *
 * 将旧的 settings.json 数据迁移到 DataStore
 */
class SettingsDataMigrator(
    private val context: Context,
    private val settingsRepository: SettingsRepositoryV2
) {
    companion object {
        private const val TAG = "SettingsDataMigrator"
        private const val LEGACY_SETTINGS_FILE = "settings.json"
        private const val MIGRATION_COMPLETED_KEY = "settings_migrated_to_datastore"
    }

    /**
     * 检查是否需要迁移
     */
    fun needsMigration(): Boolean {
        val prefs = context.getSharedPreferences("migration_prefs", Context.MODE_PRIVATE)
        val migrated = prefs.getBoolean(MIGRATION_COMPLETED_KEY, false)
        if (migrated) return false

        // 检查旧设置文件是否存在
        val legacyFile = File(context.filesDir, LEGACY_SETTINGS_FILE)
        return legacyFile.exists()
    }

    /**
     * 执行迁移
     */
    suspend fun migrate(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val legacyFile = File(context.filesDir, LEGACY_SETTINGS_FILE)
            if (!legacyFile.exists()) {
                markMigrationCompleted()
                return@withContext Result.success(Unit)
            }

            // 读取旧设置
            val jsonString = legacyFile.readText(Charsets.UTF_8)
            val jsonObject = JSONObject(jsonString)

            // 转换为 Map
            val settingsMap = mutableMapOf<String, Any?>()
            jsonObject.keys().forEach { key ->
                settingsMap[key] = jsonObject.opt(key)
            }

            // 转换为新模型
            val newSettings = SettingsMapper.fromLegacyJson(settingsMap)

            // 保存到 DataStore
            settingsRepository.updateSettings(newSettings)

            // 标记迁移完成（不删除旧文件，作为备份）
            markMigrationCompleted()

            Log.i(TAG, "Settings migration completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Settings migration failed", e)
            Result.failure(e)
        }
    }

    /**
     * 从旧设置文件读取设置（不执行迁移）
     */
    suspend fun readLegacySettings(): AppSettings? = withContext(Dispatchers.IO) {
        try {
            val legacyFile = File(context.filesDir, LEGACY_SETTINGS_FILE)
            if (!legacyFile.exists()) return@withContext null

            val jsonString = legacyFile.readText(Charsets.UTF_8)
            val jsonObject = JSONObject(jsonString)

            val settingsMap = mutableMapOf<String, Any?>()
            jsonObject.keys().forEach { key ->
                settingsMap[key] = jsonObject.opt(key)
            }

            SettingsMapper.fromLegacyJson(settingsMap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read legacy settings", e)
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
