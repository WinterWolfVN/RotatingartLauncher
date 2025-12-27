package com.app.ralaunch.controls.configs

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * 控制布局配置
 * 支持JSON序列化/反序列化
 */
@Serializable
class ControlConfig {
    @Transient
    var id: String = System.currentTimeMillis().toString()

    var name: String = "Custom Layout"
    var version: Int = 2
    var controls: MutableList<ControlData> = ArrayList()

    //region 序列化与反序列化

    /**
     * 保存配置到JSON文件（格式化输出）
     */
    fun saveTo(file: File) {
        saveTo(file.path)
    }

    /**
     * 保存配置到JSON文件（格式化输出）
     */
    fun saveTo(path: String) {
        val path = Path(path)
        val content = toJson()
        path.writeText(content)
    }

    /**
     * 转换为JSON字符串（格式化输出）
     */
    fun toJson(): String {
        return json.encodeToString(this)
    }

    /**
     * 创建当前配置的深拷贝
     * TODO: This uses JSON serialization for deep copy. Consider using a more efficient approach
     * if performance becomes an issue (e.g., manual copy constructors or a dedicated copy library).
     * Current implementation is simple and reliable but may have overhead for large objects.
     */
    fun deepCopy(): ControlConfig {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val jsonString = json.encodeToString( this)
        return json.decodeFromString(jsonString)
    }

    companion object {

        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /**
         * 从JSON文件加载配置
         */
        fun loadFrom(file: File): ControlConfig? {
            return loadFrom(file.path)
        }

        /**
         * 从JSON文件加载配置
         */
        fun loadFrom(path: String): ControlConfig? {
            val path = Path(path)
            if (!path.exists()) {
                throw IOException("Config file not found: $path")
            }

            val content = path.readText()
            return loadFromJson(content)
        }

        /**
         * 从JSON字符串加载配置
         */
        fun loadFromJson(json: String): ControlConfig? {
            return this.json.decodeFromString<ControlConfig?>(json)
        }
    }

    //endregion
}
