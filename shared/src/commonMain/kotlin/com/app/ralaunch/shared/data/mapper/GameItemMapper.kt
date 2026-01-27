package com.app.ralaunch.shared.data.mapper

import com.app.ralaunch.shared.domain.model.GameItem

/**
 * 游戏项数据映射器
 *
 * 用于新旧数据模型之间的转换
 */
object GameItemMapper {

    /**
     * 从旧版 JSON 数据转换为新模型
     */
    fun fromLegacyMap(map: Map<String, Any?>): GameItem {
        return GameItem(
            id = (map["id"] as? String) ?: generateId(),
            name = (map["gameName"] as? String) ?: "",
            description = map["gameDescription"] as? String,
            basePath = map["gameBasePath"] as? String,
            executablePath = (map["gamePath"] as? String) ?: "",
            bodyPath = map["gameBodyPath"] as? String,
            iconPath = map["iconPath"] as? String,
            modLoaderEnabled = (map["modLoaderEnabled"] as? Boolean) ?: true,
            isShortcut = (map["isShortcut"] as? Boolean) ?: false
        )
    }

    /**
     * 转换为旧版 JSON 格式（用于兼容）
     */
    fun toLegacyMap(item: GameItem): Map<String, Any?> {
        return mapOf(
            "id" to item.id,
            "gameName" to item.name,
            "gameDescription" to item.description,
            "gameBasePath" to item.basePath,
            "gamePath" to item.executablePath,
            "gameBodyPath" to item.bodyPath,
            "iconPath" to item.iconPath,
            "modLoaderEnabled" to item.modLoaderEnabled,
            "isShortcut" to item.isShortcut
        )
    }

    /**
     * 批量转换旧数据
     */
    fun fromLegacyList(list: List<Map<String, Any?>>): List<GameItem> {
        return list.map { fromLegacyMap(it) }
    }

    private var counter = 0L
    private fun generateId(): String = "game_${System.currentTimeMillis()}_${counter++}"
}

/**
 * 扩展函数：将新 GameItem 转换为旧格式
 */
fun GameItem.toLegacyFormat(): Map<String, Any?> = GameItemMapper.toLegacyMap(this)
