package com.app.ralaunch.shared.data.repository

import com.app.ralaunch.shared.domain.model.ControlLayout
import com.app.ralaunch.shared.domain.model.ControlPackManifest
import com.app.ralaunch.shared.domain.repository.ControlLayoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * 控制布局仓库实现 - 跨平台
 * 
 * 注意：文件 I/O 操作需要平台特定实现
 */
class ControlLayoutRepositoryImpl(
    private val storage: ControlLayoutStorage
) : ControlLayoutRepository {

    private val _layouts = MutableStateFlow<List<ControlLayout>>(emptyList())
    private val _currentLayoutId = MutableStateFlow<String?>(null)
    private val _availablePacks = MutableStateFlow<List<ControlPackManifest>>(emptyList())

    override fun getLayouts(): Flow<List<ControlLayout>> = _layouts.asStateFlow()

    override fun getCurrentLayout(): Flow<ControlLayout?> = _currentLayoutId.map { id ->
        _layouts.value.find { it.id == id }
    }

    override suspend fun setCurrentLayout(layoutId: String) {
        _currentLayoutId.value = layoutId
        storage.saveCurrentLayoutId(layoutId)
    }

    override suspend fun getLayoutById(id: String): ControlLayout? {
        return _layouts.value.find { it.id == id }
    }

    override suspend fun saveLayout(layout: ControlLayout) {
        val updatedList = _layouts.value.toMutableList()
        val index = updatedList.indexOfFirst { it.id == layout.id }
        if (index >= 0) {
            updatedList[index] = layout
        } else {
            updatedList.add(layout)
        }
        _layouts.value = updatedList
        storage.saveLayout(layout)
    }

    override suspend fun deleteLayout(id: String) {
        _layouts.value = _layouts.value.filter { it.id != id }
        if (_currentLayoutId.value == id) {
            _currentLayoutId.value = _layouts.value.firstOrNull()?.id
        }
        storage.deleteLayout(id)
    }

    override suspend fun importPack(packPath: String): Result<ControlLayout> {
        return storage.importPack(packPath).also { result ->
            result.getOrNull()?.let { layout ->
                saveLayout(layout)
            }
        }
    }

    override suspend fun exportLayout(layoutId: String, outputPath: String): Result<String> {
        val layout = getLayoutById(layoutId) ?: return Result.failure(
            IllegalArgumentException("Layout not found: $layoutId")
        )
        return storage.exportLayout(layout, outputPath)
    }

    override fun getAvailablePacks(): Flow<List<ControlPackManifest>> = _availablePacks.asStateFlow()

    override suspend fun refreshPacksFromRemote(): Result<List<ControlPackManifest>> {
        return storage.fetchRemotePacks().also { result ->
            result.getOrNull()?.let { packs ->
                _availablePacks.value = packs
            }
        }
    }

    /**
     * 加载所有布局（初始化时调用）
     */
    suspend fun loadLayouts() {
        _layouts.value = storage.loadAllLayouts()
        _currentLayoutId.value = storage.loadCurrentLayoutId()
    }
}

/**
 * 控制布局存储接口 - 平台实现
 */
interface ControlLayoutStorage {
    suspend fun loadAllLayouts(): List<ControlLayout>
    suspend fun saveLayout(layout: ControlLayout)
    suspend fun deleteLayout(id: String)
    suspend fun loadCurrentLayoutId(): String?
    suspend fun saveCurrentLayoutId(id: String)
    suspend fun importPack(packPath: String): Result<ControlLayout>
    suspend fun exportLayout(layout: ControlLayout, outputPath: String): Result<String>
    suspend fun fetchRemotePacks(): Result<List<ControlPackManifest>>
}
