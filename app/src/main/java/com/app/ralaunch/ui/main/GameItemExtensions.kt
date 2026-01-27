package com.app.ralaunch.ui.main

import com.app.ralaunch.data.model.GameItem
import com.app.ralaunch.shared.ui.model.GameItemUi

/**
 * GameItem 扩展函数
 */

/**
 * 将 GameItem 转换为 Compose UI 模型
 */
fun GameItem.toUiModel(): GameItemUi = GameItemUi(
    id = gameName,
    name = gameName,
    description = gameDescription,
    iconPath = iconPath,
    isShortcut = isShortcut
)

/**
 * 批量转换
 */
fun List<GameItem>.toUiModels(): List<GameItemUi> = map { it.toUiModel() }

/**
 * 构建 GameItem 映射（使用 gameName 作为 ID）
 */
fun List<GameItem>.toIdMap(): Map<String, GameItem> = 
    associateBy { it.gameName }
