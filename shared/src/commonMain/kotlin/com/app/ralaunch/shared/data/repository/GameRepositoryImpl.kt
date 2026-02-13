package com.app.ralaunch.shared.data.repository

import com.app.ralaunch.shared.domain.model.GameItem
import com.app.ralaunch.shared.domain.repository.GameRepositoryV2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 游戏仓库实现
 *
 * 使用 JSON 文件存储游戏列表
 */
class GameRepositoryImpl(
    private val gameListStorage: GameListStorage
) : GameRepositoryV2 {
    private val mutationMutex = Mutex()
    private val _gamesFlow = MutableStateFlow(loadGamesInternal())
    override val games: StateFlow<List<GameItem>> = _gamesFlow.asStateFlow()

    private fun loadGamesInternal(): List<GameItem> {
        val games = gameListStorage.loadGameList()
        attachStorage(games)
        return games
    }

    override suspend fun getById(id: String): GameItem? = games.value.find { it.id == id }

    override suspend fun upsert(game: GameItem, index: Int) = mutateAndSave { list ->
        list.removeAll { it.id == game.id }
        val insertIndex = index.coerceIn(0, list.size)
        list.add(insertIndex, game)
    }

    override suspend fun removeById(id: String) = mutateAndSave { list ->
        list.removeAll { it.id == id }
    }

    override suspend fun removeAt(index: Int) = mutateAndSave { list ->
        if (index in list.indices) list.removeAt(index)
    }

    override suspend fun reorder(from: Int, to: Int) = mutateAndSave { list ->
        if (from !in list.indices || to !in list.indices || from == to) return@mutateAndSave
        val game = list.removeAt(from)
        list.add(to, game)
    }

    override suspend fun replaceAll(games: List<GameItem>) {
        mutationMutex.withLock {
            persist(games)
        }
    }

    override suspend fun clear() {
        mutationMutex.withLock {
            persist(emptyList())
        }
    }

    private suspend inline fun mutateAndSave(crossinline mutate: (MutableList<GameItem>) -> Unit) {
        mutationMutex.withLock {
            val list = _gamesFlow.value.toMutableList()
            mutate(list)
            persist(list)
        }
    }

    private fun persist(games: List<GameItem>) {
        val immutable = games.toList()
        attachStorage(immutable)
        _gamesFlow.value = immutable
        gameListStorage.saveGameList(immutable)
    }

    private fun attachStorage(games: List<GameItem>) {
        games.forEach { it.gameListStorageParent = gameListStorage }
    }
}

/**
 * 游戏列表存储接口
 * 由各平台实现
 */
interface GameListStorage {
    fun loadGameList(): List<GameItem>
    fun saveGameList(games: List<GameItem>)
    fun getGameGlobalStorageDirFull(): String
    /**
     * 创建游戏安装目录
     * @param gameId 游戏名称（用于生成存储ID）
     * @return Pair(目录绝对路径, 存储ID)
     */
    fun createGameStorageRoot(gameId: String): Pair<String, String>
}
