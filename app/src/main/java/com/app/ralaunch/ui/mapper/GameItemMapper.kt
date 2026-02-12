package com.app.ralaunch.ui.mapper

import com.app.ralaunch.shared.domain.model.GameItem
import com.app.ralaunch.shared.ui.model.GameItemUi

/**
 * App 层 GameItem -> UI 模型转换函数
 *
 * 将跨平台的 GameItem 转换为跨平台的 GameItemUi
 */
fun GameItem.toUiModel(): GameItemUi = GameItemUi(
    id = id,
    displayedName = displayedName,
    displayedDescription = displayedDescription,
    iconPathFull = iconPathFull,  // Use absolute path for UI
    isShortcut = false,
    modLoaderEnabled = modLoaderEnabled
)

/**
 * 批量转换（自动去重）
 *
 * 使用 id 去重，确保 LazyGrid key 不会重复导致崩溃。
 */
fun List<GameItem>.toUiModels(): List<GameItemUi> =
    distinctBy { it.id }
        .map { it.toUiModel() }
