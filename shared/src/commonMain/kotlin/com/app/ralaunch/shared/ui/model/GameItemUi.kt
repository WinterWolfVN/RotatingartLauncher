package com.app.ralaunch.shared.ui.model

import com.app.ralaunch.shared.domain.model.GameItem

/**
 * 游戏项 UI 数据模型 (跨平台)
 * 
 * 用于 UI 层展示，与领域模型解耦
 */
data class GameItemUi(
    val id: String,
    val name: String,
    val description: String? = null,
    val iconPath: String? = null,
    val isShortcut: Boolean = false,
    val modLoaderEnabled: Boolean = true
)

/**
 * 领域模型 -> UI 模型转换
 */
fun GameItem.toUiModel(): GameItemUi = GameItemUi(
    id = id,
    name = name,
    description = description,
    iconPath = iconPath,
    isShortcut = isShortcut,
    modLoaderEnabled = modLoaderEnabled
)

/**
 * 批量转换
 */
fun List<GameItem>.toUiModels(): List<GameItemUi> = map { it.toUiModel() }
