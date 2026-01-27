package com.app.ralaunch.data.model

import com.app.ralaunch.installer.GameDefinition
import com.app.ralaunch.shared.domain.model.GameItem as SharedGameItem

/**
 * 游戏项数据模型 (App 层，保持向后兼容)
 *
 * 表示游戏列表中的一个游戏项，包含:
 * - 游戏基本信息（名称、描述、图标）
 * - 游戏路径（主程序集和游戏本体）
 * - ModLoader 启用状态
 */
data class GameItem(
    var gameName: String = "",
    var gameDescription: String? = null,
    var gameBasePath: String? = null,
    var gamePath: String = "",
    var gameBodyPath: String? = null,
    var iconPath: String? = null,
    var runtime: String? = null,  // 保留用于兼容旧数据，但不在 UI 中显示
    var iconResId: Int = 0,
    var modLoaderEnabled: Boolean = true,
    var isShortcut: Boolean = false
) {
    companion object {
        /**
         * 从 GameDefinition 创建 GameItem
         */
        @JvmStatic
        fun fromDefinition(
            definition: GameDefinition,
            gamePath: String,
            gameBasePath: String? = null,
            iconPath: String? = null,
            gameBodyPath: String? = null
        ): GameItem = GameItem(
            gameName = definition.displayName,
            gameBasePath = gameBasePath ?: gamePath,
            gamePath = gamePath,
            gameBodyPath = gameBodyPath,
            iconPath = iconPath,
            runtime = definition.runtime,
            modLoaderEnabled = definition.isModLoader
        )

        /**
         * 从共享模块 GameItem 转换
         */
        @JvmStatic
        fun fromShared(shared: SharedGameItem): GameItem = GameItem(
            gameName = shared.name,
            gameDescription = shared.description,
            gameBasePath = shared.basePath,
            gamePath = shared.executablePath,
            gameBodyPath = shared.bodyPath,
            iconPath = shared.iconPath,
            modLoaderEnabled = shared.modLoaderEnabled,
            isShortcut = shared.isShortcut
        )

        /**
         * 批量从共享模块转换
         */
        @JvmStatic
        fun fromSharedList(list: List<SharedGameItem>): List<GameItem> =
            list.map { fromShared(it) }
    }

    /**
     * 辅助构造函数 - 使用图标路径
     */
    constructor(gameName: String, gamePath: String, iconPath: String?) : this(
        gameName = gameName,
        gamePath = gamePath,
        iconPath = iconPath,
        iconResId = 0
    )

    /**
     * 辅助构造函数 - 使用资源ID
     */
    constructor(gameName: String, gamePath: String, iconResId: Int) : this(
        gameName = gameName,
        gamePath = gamePath,
        iconPath = "",
        iconResId = iconResId
    )

    /**
     * 转换为共享模块 GameItem
     */
    fun toShared(): SharedGameItem = SharedGameItem(
        name = gameName,
        description = gameDescription,
        basePath = gameBasePath,
        executablePath = gamePath,
        bodyPath = gameBodyPath,
        iconPath = iconPath,
        modLoaderEnabled = modLoaderEnabled,
        isShortcut = isShortcut
    )
}

/**
 * 扩展函数：SharedGameItem 转 App GameItem
 */
fun SharedGameItem.toAppModel(): GameItem = GameItem.fromShared(this)

/**
 * 扩展函数：批量转换
 */
fun List<SharedGameItem>.toAppModels(): List<GameItem> = map { it.toAppModel() }
