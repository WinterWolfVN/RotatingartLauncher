package com.app.ralaunch.shared.domain.repository

import com.app.ralaunch.shared.domain.model.GameItem
import kotlinx.coroutines.flow.Flow

/**
 * 游戏数据仓库接口
 *
 * 定义游戏列表的 CRUD 操作
 */
interface GameRepository {

    /**
     * 获取游戏列表 (Flow)
     */
    fun getGames(): Flow<List<GameItem>>

    /**
     * 获取游戏列表 (同步)
     */
    suspend fun getGameList(): List<GameItem>

    /**
     * 根据 ID 获取游戏
     */
    suspend fun getGameById(id: String): GameItem?

    /**
     * 添加游戏
     * @param game 游戏项
     * @param position 插入位置，默认为列表开头
     */
    suspend fun addGame(game: GameItem, position: Int = 0)

    /**
     * 更新游戏
     */
    suspend fun updateGame(game: GameItem)

    /**
     * 删除游戏
     */
    suspend fun deleteGame(id: String)

    /**
     * 删除指定位置的游戏
     */
    suspend fun deleteGameAt(position: Int)

    /**
     * 移动游戏位置
     */
    suspend fun moveGame(fromPosition: Int, toPosition: Int)

    /**
     * 清空所有游戏
     */
    suspend fun clearAll()

    /**
     * 保存整个游戏列表
     */
    suspend fun saveGameList(games: List<GameItem>)
}
