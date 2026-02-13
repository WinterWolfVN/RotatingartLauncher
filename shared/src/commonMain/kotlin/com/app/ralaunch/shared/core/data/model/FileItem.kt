package com.app.ralaunch.shared.core.data.model

import kotlinx.serialization.Serializable

/**
 * 文件项数据模型 - 跨平台共享
 */
@Serializable
data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isParentDirectory: Boolean = false
) {
    /**
     * 获取文件扩展名（小写）
     */
    fun getExtension(): String {
        val lastDot = name.lastIndexOf('.')
        return if (lastDot > 0 && lastDot < name.length - 1) {
            name.substring(lastDot + 1).lowercase()
        } else {
            ""
        }
    }

    /**
     * 检查是否为特定扩展名
     */
    fun hasExtension(vararg extensions: String): Boolean {
        val ext = getExtension()
        return extensions.any { it.lowercase() == ext }
    }
}

/**
 * 组件项数据模型 - 用于初始化过程
 */
@Serializable
data class ComponentItem(
    val name: String,
    val description: String,
    val fileName: String,
    val needsExtraction: Boolean = true,
    var isInstalled: Boolean = false,
    var progress: Int = 0
)
