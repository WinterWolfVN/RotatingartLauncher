package com.app.ralaunch.shared.domain.repository

import com.app.ralaunch.shared.domain.model.ControlLayout
import com.app.ralaunch.shared.domain.model.ControlPackManifest
import kotlinx.coroutines.flow.StateFlow

/**
 * 控制布局仓库 V2
 */
interface ControlLayoutRepositoryV2 {
    val layouts: StateFlow<List<ControlLayout>>
    val currentLayout: StateFlow<ControlLayout?>
    val availablePacks: StateFlow<List<ControlPackManifest>>

    suspend fun setCurrentLayout(layoutId: String)
    suspend fun getLayoutById(id: String): ControlLayout?
    suspend fun saveLayout(layout: ControlLayout)
    suspend fun deleteLayout(id: String)
    suspend fun importPack(packPath: String): Result<ControlLayout>
    suspend fun exportLayout(layoutId: String, outputPath: String): Result<String>
    suspend fun refreshPacksFromRemote(): Result<List<ControlPackManifest>>
}
