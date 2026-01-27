package com.app.ralaunch.shared.data.local

import android.content.Context
import com.app.ralaunch.shared.data.repository.GameListStorage
import com.app.ralaunch.shared.domain.model.GameItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Android 平台游戏列表存储实现
 */
class AndroidGameListStorage(
    private val context: Context
) : GameListStorage {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val gamesDir: File
        get() = File(context.getExternalFilesDir(null), "games").also {
            if (!it.exists()) it.mkdirs()
        }

    private val gameListFile: File
        get() = File(gamesDir, DataStoreConstants.GAME_LIST_FILE)

    override fun loadGameList(): List<GameItem> {
        return try {
            if (!gameListFile.exists()) return emptyList()
            val jsonString = gameListFile.readText(Charsets.UTF_8)
            json.decodeFromString<List<GameItem>>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun saveGameList(games: List<GameItem>) {
        try {
            if (!gameListFile.exists()) {
                gameListFile.createNewFile()
            }
            val jsonString = json.encodeToString(games)
            gameListFile.writeText(jsonString, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
