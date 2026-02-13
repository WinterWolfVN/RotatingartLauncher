package com.app.ralaunch.shared.core.contract.service

import kotlinx.coroutines.flow.Flow

/**
 * 补丁信息
 */
data class PatchInfo(
    val id: String,
    val name: String,
    val description: String = "",
    val version: String = "1.0.0",
    val author: String = "",
    val targetGames: List<String> = emptyList(),
    val isBuiltIn: Boolean = false,
    val isEnabled: Boolean = false,
    val installPath: String? = null
)

/**
 * 补丁兼容性结果
 */
data class PatchCompatibility(
    val isCompatible: Boolean,
    val reason: String? = null
)

/**
 * 补丁服务接口
 * 
 * 管理游戏补丁的安装、启用、禁用等操作
 */
interface PatchService {
    
    /**
     * 已安装的补丁列表
     */
    val installedPatches: Flow<List<PatchInfo>>
    
    /**
     * 获取所有已安装的补丁
     */
    suspend fun getInstalledPatches(): List<PatchInfo>
    
    /**
     * 获取指定游戏可用的补丁
     * @param gameId 游戏 ID
     */
    suspend fun getPatchesForGame(gameId: String): List<PatchInfo>
    
    /**
     * 获取补丁信息
     * @param patchId 补丁 ID
     */
    suspend fun getPatchInfo(patchId: String): PatchInfo?
    
    /**
     * 检查补丁与游戏的兼容性
     * @param patchId 补丁 ID
     * @param gameId 游戏 ID
     */
    suspend fun checkCompatibility(patchId: String, gameId: String): PatchCompatibility
    
    /**
     * 启用补丁
     * @param patchId 补丁 ID
     * @param gameId 游戏 ID
     */
    suspend fun enablePatch(patchId: String, gameId: String): Boolean
    
    /**
     * 禁用补丁
     * @param patchId 补丁 ID
     * @param gameId 游戏 ID
     */
    suspend fun disablePatch(patchId: String, gameId: String): Boolean
    
    /**
     * 获取游戏已启用的补丁列表
     * @param gameId 游戏 ID
     */
    suspend fun getEnabledPatches(gameId: String): List<PatchInfo>
    
    /**
     * 安装补丁
     * @param patchPath 补丁文件路径
     * @return 安装的补丁信息
     */
    suspend fun installPatch(patchPath: String): Result<PatchInfo>
    
    /**
     * 卸载补丁
     * @param patchId 补丁 ID
     */
    suspend fun uninstallPatch(patchId: String): Boolean
    
    /**
     * 安装内置补丁
     */
    suspend fun installBuiltInPatches()
    
    /**
     * 构建启动钩子环境变量
     * @param patches 启用的补丁列表
     */
    fun buildStartupHooksEnvVar(patches: List<PatchInfo>): String?
}
