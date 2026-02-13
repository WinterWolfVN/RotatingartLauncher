package com.app.ralaunch.shared.core.data.repository

import com.app.ralaunch.shared.core.model.domain.ControlLayout
import com.app.ralaunch.shared.core.model.domain.ControlPackManifest
import com.app.ralaunch.shared.core.contract.repository.ControlLayoutRepositoryV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 控制布局仓库实现 - 跨平台
 * 
 * 注意：文件 I/O 操作需要平台特定实现
 */
class ControlLayoutRepositoryImpl(
    private val storage: ControlLayoutStorage
) : ControlLayoutRepositoryV2 {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    private val _layouts = MutableStateFlow<List<ControlLayout>>(emptyList())
    private val _currentLayoutId = MutableStateFlow<String?>(null)
    private val _availablePacks = MutableStateFlow<List<ControlPackManifest>>(emptyList())

    override val layouts: StateFlow<List<ControlLayout>> = _layouts.asStateFlow()
    override val currentLayout: StateFlow<ControlLayout?> = combine(_layouts, _currentLayoutId) { list, id ->
        list.find { it.id == id }
    }.stateIn(
        scope = repositoryScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )
    override val availablePacks: StateFlow<List<ControlPackManifest>> = _availablePacks.asStateFlow()

    init {
        repositoryScope.launch {
            ensureInitialized()
        }
    }

    override suspend fun setCurrentLayout(layoutId: String) {
        ensureInitialized()
        _currentLayoutId.value = layoutId
        storage.saveCurrentLayoutId(layoutId)
    }

    override suspend fun getLayoutById(id: String): ControlLayout? {
        ensureInitialized()
        return layouts.value.find { it.id == id }
    }

    override suspend fun saveLayout(layout: ControlLayout) {
        ensureInitialized()
        val updatedList = layouts.value.toMutableList()
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
        ensureInitialized()
        _layouts.value = layouts.value.filter { it.id != id }
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
        ensureInitialized()
        val layout = getLayoutById(layoutId) ?: return Result.failure(
            IllegalArgumentException("Layout not found: $layoutId")
        )
        return storage.exportLayout(layout, outputPath)
    }

    override suspend fun refreshPacksFromRemote(): Result<List<ControlPackManifest>> {
        return storage.fetchRemotePacks().also { result ->
            result.getOrNull()?.let { packs ->
                _availablePacks.value = packs
            }
        }
    }

    private suspend fun ensureInitialized() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            _layouts.value = storage.loadAllLayouts()
            _currentLayoutId.value = storage.loadCurrentLayoutId()
            initialized = true
        }
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
