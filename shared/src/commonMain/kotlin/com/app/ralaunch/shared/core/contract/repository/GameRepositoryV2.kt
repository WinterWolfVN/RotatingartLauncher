package com.app.ralaunch.shared.core.contract.repository

import com.app.ralaunch.shared.core.model.domain.GameItem
import kotlinx.coroutines.flow.StateFlow

/**
 * 游戏数据仓库 V2
 *
 * 以 StateFlow 作为单一数据源，读写接口保持最小集合。
 */
interface GameRepositoryV2 {
    val games: StateFlow<List<GameItem>>

    suspend fun getById(id: String): GameItem?
    suspend fun upsert(game: GameItem, index: Int = 0)
    suspend fun removeById(id: String)
    suspend fun removeAt(index: Int)
    suspend fun reorder(from: Int, to: Int)
    suspend fun replaceAll(games: List<GameItem>)
    suspend fun clear()
}
