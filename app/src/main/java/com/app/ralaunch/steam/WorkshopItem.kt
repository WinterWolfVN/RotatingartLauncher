package com.app.ralaunch.steam

import java.io.File

/**
 * 创意工坊物品数据模型
 */
data class WorkshopItem(
    val workshopId: String,
    val appId: String,
    val localPath: File? = null,
    val downloadedAt: Long = 0
) {
    /**
     * 是否已下载
     */
    val isDownloaded: Boolean
        get() = localPath?.exists() == true
    
    /**
     * 获取物品名称（从目录名或 workshop ID）
     */
    val displayName: String
        get() = localPath?.name ?: workshopId
    
    /**
     * 获取文件大小
     */
    val sizeBytes: Long
        get() = localPath?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0
    
    /**
     * 格式化文件大小
     */
    val formattedSize: String
        get() {
            val bytes = sizeBytes
            return when {
                bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
                bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
                bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
                else -> "$bytes B"
            }
        }
}

/**
 * 下载状态
 */
sealed class WorkshopDownloadState {
    data object Idle : WorkshopDownloadState()
    data object Installing : WorkshopDownloadState()
    data class Downloading(val output: List<String>) : WorkshopDownloadState()
    data class Success(val item: WorkshopItem) : WorkshopDownloadState()
    data class Error(val message: String) : WorkshopDownloadState()
}

/**
 * 预设的 Steam AppID
 */
object SteamAppIds {
    const val TMODLOADER = "1281930"
    const val TERRARIA = "105600"
    
    val SUPPORTED_APPS = mapOf(
        TMODLOADER to "TModLoader",
        TERRARIA to "Terraria"
    )
}
