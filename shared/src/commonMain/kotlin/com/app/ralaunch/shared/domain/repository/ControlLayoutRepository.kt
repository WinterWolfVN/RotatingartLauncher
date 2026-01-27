package com.app.ralaunch.shared.domain.repository

import com.app.ralaunch.shared.domain.model.ControlLayout
import com.app.ralaunch.shared.domain.model.ControlPackManifest
import kotlinx.coroutines.flow.Flow

/**
 * 控制布局仓库接口
 */
interface ControlLayoutRepository {

    /**
     * 获取所有布局列表
     */
    fun getLayouts(): Flow<List<ControlLayout>>

    /**
     * 获取当前激活的布局
     */
    fun getCurrentLayout(): Flow<ControlLayout?>

    /**
     * 设置当前布局
     */
    suspend fun setCurrentLayout(layoutId: String)

    /**
     * 根据 ID 获取布局
     */
    suspend fun getLayoutById(id: String): ControlLayout?

    /**
     * 保存布局
     */
    suspend fun saveLayout(layout: ControlLayout)

    /**
     * 删除布局
     */
    suspend fun deleteLayout(id: String)

    /**
     * 导入布局包
     */
    suspend fun importPack(packPath: String): Result<ControlLayout>

    /**
     * 导出布局为包
     */
    suspend fun exportLayout(layoutId: String, outputPath: String): Result<String>

    /**
     * 获取可用的控件包列表
     */
    fun getAvailablePacks(): Flow<List<ControlPackManifest>>

    /**
     * 从远程仓库刷新控件包
     */
    suspend fun refreshPacksFromRemote(): Result<List<ControlPackManifest>>
}
