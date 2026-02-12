package com.app.ralaunch.shared.data.local

import android.content.Context
import com.app.ralaunch.shared.AppConstants
import com.app.ralaunch.shared.data.repository.GameListStorage
import com.app.ralaunch.shared.domain.model.GameItem
import com.app.ralaunch.shared.domain.model.GameList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Android 平台游戏列表存储实现
 *
 * 存储结构:
 * ```
 * games/
 * ├── game_list.json          (游戏列表索引)
 * ├── GameA_hash1/
 * │   ├── game_info.json      (GameItem 序列化)
 * │   ├── A.exe
 * │   └── icon.png
 * └── GameB_hash2/
 *     ├── game_info.json
 *     └── B.exe
 * ```
 *
 * @property context Android 应用上下文
 */
class AndroidGameListStorage(
    private val context: Context
) : GameListStorage {

    /** JSON 序列化器配置 */
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 全局游戏存储目录: {externalFilesDir}/games/ */
    private val gamesGlobalStorageDir: File
        get() = File(context.getExternalFilesDir(null), AppConstants.Dirs.GAMES).also {
            if (!it.exists()) it.mkdirs()
        }

    /** 游戏列表索引文件: {gamesGlobalStorageDir}/game_list.json */
    private val gameListJsonFile: File
        get() = File(gamesGlobalStorageDir, AppConstants.Files.GAME_LIST)

    /**
     * 获取游戏存储根目录
     * @param storageId 游戏存储 ID
     * @return 游戏存储根目录 File 对象
     */
    private fun getGameStorageRootFull(storageId: String): File {
        return File(gamesGlobalStorageDir, storageId).also {
            if (!it.exists()) it.mkdirs()
        }
    }

    /**
     * 获取游戏信息文件
     * @param storageId 游戏存储 ID
     * @return game_info.json File 对象
     */
    private fun getGameInfoFile(storageId: String): File {
        return File(getGameStorageRootFull(storageId), AppConstants.Files.GAME_INFO)
    }

    /**
     * 加载游戏列表
     *
     * 从 game_list.json 读取游戏列表索引，
     * 再逐个加载每个游戏的 game_info.json
     *
     * @return 游戏列表，加载失败返回空列表
     */
    override fun loadGameList(): List<GameItem> {
        return try {
            // 读取游戏列表索引
            if (!gameListJsonFile.exists()) return emptyList()

            val gameListJson = gameListJsonFile.readText(Charsets.UTF_8)
            val gameList = json.decodeFromString<GameList>(gameListJson)

            // 读取每个游戏的信息
            gameList.games.mapNotNull { gameDirName ->
                loadGameInfo(gameDirName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 加载单个游戏信息 (直接使用 GameItem 序列化)
     *
     * @param storageId 游戏存储 ID
     * @return GameItem 对象，加载失败返回 null
     */
    private fun loadGameInfo(storageId: String): GameItem? {
        return try {
            val gameInfoFile = getGameInfoFile(storageId)
            if (!gameInfoFile.exists()) return null

            val jsonString = gameInfoFile.readText(Charsets.UTF_8)
            json.decodeFromString<GameItem>(jsonString).also {
                it.gameListStorageParent = this // 设置父级存储引用
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 保存游戏列表
     *
     * 1. 更新 game_list.json 索引
     * 2. 保存每个游戏的 game_info.json
     * 3. 清理已删除游戏的目录
     *
     * @param games 游戏列表
     */
    override fun saveGameList(games: List<GameItem>) {
        try {
            // 确保games目录存在
            if (!gamesGlobalStorageDir.exists()) {
                gamesGlobalStorageDir.mkdirs()
            }

            // 更新游戏列表索引
            val gameList = GameList(games = games.map { it.id })
            val gameListJson = json.encodeToString(gameList)
            gameListJsonFile.writeText(gameListJson, Charsets.UTF_8)

            // 保存每个游戏的信息 (直接序列化 GameItem)
            games.forEach { game ->
                saveGameInfo(game)
            }

            // 清理已删除游戏的目录
            cleanupDeletedGameStorageRoots(games.map { it.id })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取全局游戏存储目录的绝对路径
     *
     * @return 游戏存储目录绝对路径
     */
    override fun getGameGlobalStorageDirFull(): String {
        return gamesGlobalStorageDir.absolutePath
    }

    /**
     * 创建新游戏的存储目录
     *
     * 生成唯一的存储 ID (sanitizedGameId_hash) 并创建对应目录
     *
     * @param gameId 游戏标识符
     * @return Pair(目录绝对路径, 存储 ID)
     */
    override fun createGameStorageRoot(gameId: String): Pair<String, String> {
        // 生成存储 ID
        val baseName = gameId.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_")
        val hash = UUID.randomUUID().toString().replace("-", "").take(8)
        val storageId = "${baseName}_$hash"

        // 创建目录
        val gameDir = File(gamesGlobalStorageDir, storageId)
        gameDir.mkdirs()

        return Pair(gameDir.absolutePath, storageId)
    }

    /**
     * 保存单个游戏信息 (直接使用 GameItem 序列化)
     */
    private fun saveGameInfo(game: GameItem) {
        val gameInfoFile = getGameInfoFile(game.id)

        try {
            val jsonString = json.encodeToString(game)
            gameInfoFile.writeText(jsonString, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 清理已删除游戏的目录
     */
    private fun cleanupDeletedGameStorageRoots(keepRoots: List<String>) {
        gamesGlobalStorageDir.listFiles()?.forEach { file ->
            if (file.isDirectory && file.name !in keepRoots) {
                file.deleteRecursively()
            }
        }
    }
}
