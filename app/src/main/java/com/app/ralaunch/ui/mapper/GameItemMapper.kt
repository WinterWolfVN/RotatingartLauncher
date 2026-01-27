package com.app.ralaunch.ui.mapper

import com.app.ralaunch.data.model.GameItem
import com.app.ralaunch.shared.ui.model.GameItemUi

/**
 * App 层 GameItem -> UI 模型转换函数
 * 
 * 将 Android 层的 GameItem 转换为跨平台的 GameItemUi
 * 使用 gameName + gamePath 的组合生成唯一 ID，避免仅使用 hashCode 导致的冲突
 */
fun GameItem.toUiModel(): GameItemUi = GameItemUi(
    id = generateUniqueId(),
    name = gameName,
    description = gameDescription,
    iconPath = iconPath,
    isShortcut = isShortcut,
    modLoaderEnabled = modLoaderEnabled
)

/**
 * 生成唯一 ID
 * 使用游戏名和路径的组合哈希，确保同名游戏有不同的 ID
 */
private fun GameItem.generateUniqueId(): String {
    return "${gameName}_${gamePath}".hashCode().toString()
}

/**
 * 批量转换
 */
fun List<GameItem>.toUiModels(): List<GameItemUi> = map { it.toUiModel() }
