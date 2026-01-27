package com.app.ralaunch.data.repository

import com.app.ralaunch.data.model.GameItem
import com.app.ralaunch.data.model.toAppModel
import com.app.ralaunch.data.model.toAppModels
import com.app.ralaunch.shared.domain.repository.GameRepository as SharedGameRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import com.app.ralaunch.shared.domain.model.GameItem as SharedGameItem

/**
 * 旧版 GameRepository 接口 (兼容层)
 *
 * 保持与旧代码的兼容性
 */
interface GameRepository {
    fun loadGameList(): List<GameItem>
    fun saveGameList(games: List<GameItem>)
    fun addGame(game: GameItem)
    fun removeGame(position: Int)
    fun updateGame(position: Int, game: GameItem)
}

/**
 * 兼容层实现
 *
 * 桥接旧的 GameRepository 接口到新的 SharedGameRepository
 */
class LegacyGameRepositoryBridge(
    private val sharedRepository: SharedGameRepository
) : GameRepository {

    override fun loadGameList(): List<GameItem> {
        return runBlocking {
            sharedRepository.getGameList().map { it.toAppModel() }
        }
    }

    override fun saveGameList(games: List<GameItem>) {
        runBlocking {
            sharedRepository.saveGameList(games.map { it.toShared() })
        }
    }

    override fun addGame(game: GameItem) {
        runBlocking {
            sharedRepository.addGame(game.toShared(), 0)
        }
    }

    override fun removeGame(position: Int) {
        runBlocking {
            sharedRepository.deleteGameAt(position)
        }
    }

    override fun updateGame(position: Int, game: GameItem) {
        runBlocking {
            sharedRepository.updateGame(game.toShared())
        }
    }

    // ==================== 扩展方法（使用协程） ====================

    /**
     * 获取游戏列表 Flow（推荐使用）
     */
    fun getGamesFlow(): Flow<List<GameItem>> {
        return sharedRepository.getGames().map { list ->
            list.toAppModels()
        }
    }

    /**
     * 异步添加游戏
     */
    suspend fun addGameAsync(game: GameItem) {
        sharedRepository.addGame(game.toShared(), 0)
    }

    /**
     * 异步删除游戏
     */
    suspend fun removeGameAsync(position: Int) {
        sharedRepository.deleteGameAt(position)
    }

    /**
     * 异步更新游戏
     */
    suspend fun updateGameAsync(game: GameItem) {
        sharedRepository.updateGame(game.toShared())
    }
}

/**
 * 扩展函数：将 App GameItem 转换为 Shared GameItem
 */
private fun GameItem.toShared(): SharedGameItem = this.toShared()
