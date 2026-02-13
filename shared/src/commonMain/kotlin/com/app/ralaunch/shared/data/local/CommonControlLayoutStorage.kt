package com.app.ralaunch.shared.data.local

import com.app.ralaunch.shared.AppConstants
import com.app.ralaunch.shared.data.repository.ControlLayoutStorage
import com.app.ralaunch.shared.domain.model.ControlLayout
import com.app.ralaunch.shared.domain.model.ControlPackManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * 控制布局存储通用实现。
 *
 * 平台层仅提供目录路径。
 */
@OptIn(ExperimentalPathApi::class)
class CommonControlLayoutStorage(
    private val pathsProvider: StoragePathsProvider
) : ControlLayoutStorage {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override suspend fun loadAllLayouts(): List<ControlLayout> = withContext(Dispatchers.Default) {
        layoutsDirPathFull.createDirectories()
        layoutsDirPathFull.listDirectoryEntries()
            .filter { it.isDirectory().not() }
            .filter { it.name.endsWith(".json") }
            .filter { it.name != AppConstants.Files.CONTROL_LAYOUT_STATE }
            .mapNotNull { filePathFull ->
                runCatching {
                    json.decodeFromString<ControlLayout>(filePathFull.readText())
                }.getOrNull()
            }
    }

    override suspend fun saveLayout(layout: ControlLayout) = withContext(Dispatchers.Default) {
        layoutsDirPathFull.createDirectories()
        layoutFilePathFull(layout.id).writeText(json.encodeToString(layout))
    }

    override suspend fun deleteLayout(id: String) = withContext(Dispatchers.Default) {
        layoutFilePathFull(id).deleteIfExists()
        Unit
    }

    override suspend fun loadCurrentLayoutId(): String? = withContext(Dispatchers.Default) {
        if (!layoutStatePathFull.exists()) return@withContext null
        runCatching {
            json.decodeFromString<ControlLayoutState>(layoutStatePathFull.readText()).currentLayoutId
        }.getOrNull()
    }

    override suspend fun saveCurrentLayoutId(id: String) = withContext(Dispatchers.Default) {
        layoutsDirPathFull.createDirectories()
        val state = ControlLayoutState(currentLayoutId = id)
        layoutStatePathFull.writeText(json.encodeToString(state))
    }

    override suspend fun importPack(packPath: String): Result<ControlLayout> = withContext(Dispatchers.Default) {
        runCatching {
            val packPathFull = Path(packPath)
            if (!packPathFull.exists()) {
                throw IllegalArgumentException("File not found: $packPath")
            }

            val layout = json.decodeFromString<ControlLayout>(packPathFull.readText())
            saveLayout(layout)
            layout
        }
    }

    override suspend fun exportLayout(layout: ControlLayout, outputPath: String): Result<String> =
        withContext(Dispatchers.Default) {
            runCatching {
                val outputPathFull = Path(outputPath)
                outputPathFull.parent?.createDirectories()
                outputPathFull.writeText(json.encodeToString(layout))
                outputPath
            }
        }

    override suspend fun fetchRemotePacks(): Result<List<ControlPackManifest>> {
        // 远程获取控件包功能暂未实现
        return Result.success(emptyList())
    }

    private val layoutsDirPathFull
        get() = Path(pathsProvider.controlLayoutsDirPathFull())

    private fun layoutFilePathFull(layoutId: String) =
        layoutsDirPathFull.resolve("$layoutId.json")

    private val layoutStatePathFull
        get() = layoutsDirPathFull.resolve(AppConstants.Files.CONTROL_LAYOUT_STATE)
}

@Serializable
private data class ControlLayoutState(
    val currentLayoutId: String? = null
)
