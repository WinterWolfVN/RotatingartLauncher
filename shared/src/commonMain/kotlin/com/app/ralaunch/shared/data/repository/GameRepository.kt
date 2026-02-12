package com.app.ralaunch.shared.data.repository

import com.app.ralaunch.shared.domain.model.GameItem

/**
 * 游戏仓库接口 - 跨平台共享
 */
interface GameRepository {
    /**
     * 获取所有游戏列表
     */
    suspend fun getGames(): List<GameItem>

    /**
     * 添加游戏
     */
    suspend fun addGame(game: GameItem)

    /**
     * 删除游戏
     */
    suspend fun removeGame(game: GameItem)

    /**
     * 更新游戏
     */
    suspend fun updateGame(game: GameItem)

    /**
     * 保存游戏列表
     */
    suspend fun saveGames(games: List<GameItem>)

    /**
     * 根据路径查找游戏
     */
    suspend fun findGameByPath(assemblyPath: String): GameItem?
}

/**
 * 设置仓库接口 - 跨平台共享
 */
interface SettingsRepository {
    fun getThemeMode(): Int
    fun setThemeMode(mode: Int)

    fun isLegalAgreed(): Boolean
    fun setLegalAgreed(agreed: Boolean)

    fun isPermissionsGranted(): Boolean
    fun setPermissionsGranted(granted: Boolean)

    fun isComponentsExtracted(): Boolean
    fun setComponentsExtracted(extracted: Boolean)

    fun getVideoBackgroundEnabled(): Boolean
    fun setVideoBackgroundEnabled(enabled: Boolean)

    fun getVideoBackgroundSpeed(): Float
    fun setVideoBackgroundSpeed(speed: Float)

    fun getVideoBackgroundOpacity(): Float
    fun setVideoBackgroundOpacity(opacity: Float)
}
