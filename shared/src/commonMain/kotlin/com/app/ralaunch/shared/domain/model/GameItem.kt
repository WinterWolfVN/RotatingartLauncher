package com.app.ralaunch.shared.domain.model

import kotlinx.serialization.Serializable

/**
 * 游戏项数据模型 (跨平台版本)
 *
 * 表示游戏列表中的一个游戏项
 */
@Serializable
data class GameItem(
    val id: String = generateId(),
    val name: String,
    val description: String? = null,
    val basePath: String? = null,
    val executablePath: String,
    val bodyPath: String? = null,
    val iconPath: String? = null,
    val modLoaderEnabled: Boolean = true,
    val isShortcut: Boolean = false
) {
    companion object {
        private var counter = 0L

        private fun generateId(): String {
            return "game_${System.currentTimeMillis()}_${counter++}"
        }

        /**
         * 从旧版 GameItem 创建（用于迁移）
         */
        fun fromLegacy(
            gameName: String,
            gamePath: String,
            gameBasePath: String? = null,
            gameBodyPath: String? = null,
            iconPath: String? = null,
            modLoaderEnabled: Boolean = true,
            isShortcut: Boolean = false,
            gameDescription: String? = null
        ): GameItem = GameItem(
            name = gameName,
            description = gameDescription,
            basePath = gameBasePath ?: gamePath,
            executablePath = gamePath,
            bodyPath = gameBodyPath,
            iconPath = iconPath,
            modLoaderEnabled = modLoaderEnabled,
            isShortcut = isShortcut
        )
    }

    /**
     * 获取用于删除的路径（优先使用 basePath）
     */
    val deletionPath: String
        get() = basePath ?: executablePath

    /**
     * 是否有有效图标
     */
    val hasIcon: Boolean
        get() = !iconPath.isNullOrBlank()
}
