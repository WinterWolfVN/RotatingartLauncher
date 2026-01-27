package com.app.ralaunch.shared.data.local

import android.content.Context
import com.app.ralaunch.shared.data.repository.ControlLayoutStorage
import com.app.ralaunch.shared.domain.model.ControlLayout
import com.app.ralaunch.shared.domain.model.ControlPackManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Android 控制布局存储实现
 */
class AndroidControlLayoutStorage(
    private val context: Context
) : ControlLayoutStorage {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val layoutsDir: File
        get() = File(context.filesDir, "control_layouts").apply {
            if (!exists()) mkdirs()
        }

    private val prefsName = "control_layout_prefs"
    private val keyCurrentLayoutId = "current_layout_id"

    override suspend fun loadAllLayouts(): List<ControlLayout> = withContext(Dispatchers.IO) {
        layoutsDir.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<ControlLayout>(file.readText())
                } catch (e: Exception) {
                    null
                }
            }
            ?: emptyList()
    }

    override suspend fun saveLayout(layout: ControlLayout) = withContext(Dispatchers.IO) {
        val file = File(layoutsDir, "${layout.id}.json")
        file.writeText(json.encodeToString(layout))
    }

    override suspend fun deleteLayout(id: String) {
        withContext(Dispatchers.IO) {
            File(layoutsDir, "$id.json").delete()
        }
    }

    override suspend fun loadCurrentLayoutId(): String? = withContext(Dispatchers.IO) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getString(keyCurrentLayoutId, null)
    }

    override suspend fun saveCurrentLayoutId(id: String) = withContext(Dispatchers.IO) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(keyCurrentLayoutId, id)
            .apply()
    }

    override suspend fun importPack(packPath: String): Result<ControlLayout> = withContext(Dispatchers.IO) {
        try {
            val file = File(packPath)
            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("File not found: $packPath"))
            }

            val layout = json.decodeFromString<ControlLayout>(file.readText())
            saveLayout(layout)
            Result.success(layout)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exportLayout(layout: ControlLayout, outputPath: String): Result<String> = 
        withContext(Dispatchers.IO) {
            try {
                val file = File(outputPath)
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(layout))
                Result.success(outputPath)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun fetchRemotePacks(): Result<List<ControlPackManifest>> {
        // 远程获取控件包功能暂未实现
        return Result.success(emptyList())
    }
}
