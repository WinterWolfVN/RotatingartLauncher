package com.app.ralaunch.ui.main

import com.app.ralaunch.shared.domain.model.GameItem
import com.app.ralaunch.shared.ui.model.GameItemUi

/**
 * GameItem 扩展函数
 */

/**
 * 将 GameItem 转换为 Compose UI 模型
 */
fun GameItem.toUiModel(): GameItemUi = GameItemUi(
    id = id,
    displayedName = displayedName,
    displayedDescription = displayedDescription,
    iconPathFull = iconPathFull,  // Use absolute path for UI
    isShortcut = false
)

/**
 * 批量转换
 */
fun List<GameItem>.toUiModels(): List<GameItemUi> = map { it.toUiModel() }

/**
 * 构建 GameItem 映射（使用 id 作为唯一标识）
 */
fun List<GameItem>.toIdMap(): Map<String, GameItem> =
    associateBy { it.id }
