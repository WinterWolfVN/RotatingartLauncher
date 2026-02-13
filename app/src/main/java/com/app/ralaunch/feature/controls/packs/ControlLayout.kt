package com.app.ralaunch.feature.controls.packs

import com.app.ralaunch.feature.controls.ControlData
import com.app.ralaunch.core.common.util.AppLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * 子布局配置
 * 
 * 每个控件包可以包含多个子布局（如"建筑"、"战斗"等模式），
 * 游戏内可以快速切换子布局。
 */
@Serializable
data class SubLayout(
    /** 子布局 ID */
    var id: String = UUID.randomUUID().toString(),
    
    /** 子布局名称（如"建筑"、"战斗"、"默认"） */
    var name: String = "Default",
    
    /** 子布局专属控件列表 */
    var controls: MutableList<ControlData> = mutableListOf()
) {
    /**
     * 创建当前子布局的深拷贝
     */
    fun deepCopy(): SubLayout {
        val json = ControlLayout.json
        val jsonString = json.encodeToString(serializer(), this)
        return try {
            json.decodeFromString<SubLayout>(jsonString).also { it.id = this.id }
        } catch (e: Exception) {
            this
        }
    }
}

/**
 * 控件布局配置
 * 
 * 包含布局的所有控件数据，支持 JSON 序列化/反序列化
 * 替代原有的 ControlConfig
 * 
 * 布局结构:
 * - controls: 共享控件列表（始终显示，不随子布局切换而变化）
 * - subLayouts: 子布局列表（如"建筑"、"战斗"等模式的专属控件）
 * - activeSubLayoutId: 当前激活的子布局 ID
 * 
 * 向后兼容: 无 subLayouts 的旧布局将 controls 视为全部控件。
 */
@Serializable
data class ControlLayout(
    /** 布局名称 */
    var name: String = "Custom Layout",
    
    /** 布局版本（用于兼容性检查） */
    var version: Int = 2,
    
    /** 共享控件列表（始终显示） */
    var controls: MutableList<ControlData> = mutableListOf(),
    
    /** 子布局列表 */
    var subLayouts: MutableList<SubLayout> = mutableListOf(),
    
    /** 当前激活的子布局 ID */
    var activeSubLayoutId: String? = null
) {
    /** 布局 ID（运行时使用，不序列化） */
    @Transient
    var id: String = "pack_${System.currentTimeMillis()}"

    fun toJson(): String = json.encodeToString(serializer(), this)

    fun saveTo(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(toJson())
    }

    fun saveTo(path: String) = saveTo(File(path))

    /**
     * 创建当前布局的深拷贝
     */
    fun deepCopy(): ControlLayout {
        val jsonString = toJson()
        return loadFromJson(jsonString)?.copy()?.also { it.id = this.id } ?: this
    }

    // ========== 子布局相关方法 ==========
    
    /**
     * 获取当前激活的子布局
     * 
     * @return 激活的子布局，如果没有匹配则返回第一个子布局，如果没有子布局返回 null
     */
    fun getActiveSubLayout(): SubLayout? =
        subLayouts.find { it.id == activeSubLayoutId } ?: subLayouts.firstOrNull()

    /**
     * 获取当前应显示的所有控件（共享控件 + 激活子布局的控件）
     * 
     * - 无子布局时: 返回 controls 列表（向后兼容旧布局）
     * - 有子布局时: 返回 controls（共享）+ 激活子布局的 controls
     */
    fun getVisibleControls(): List<ControlData> {
        if (subLayouts.isEmpty()) return controls  // 向后兼容
        return controls + (getActiveSubLayout()?.controls ?: emptyList())
    }
    
    /**
     * 是否包含子布局
     */
    fun hasSubLayouts(): Boolean = subLayouts.isNotEmpty()

    companion object {
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun loadFrom(file: File): ControlLayout? = loadFrom(file.path)

        fun loadFrom(path: String): ControlLayout? {
            val file = File(path)
            if (!file.exists()) {
                throw IOException("Layout file not found: $path")
            }
            return loadFromJson(file.readText())
        }

        fun loadFromJson(jsonString: String): ControlLayout? {
            return try {
                val layout = json.decodeFromString<ControlLayout>(jsonString)
                sanitizeControls(layout)
                layout
            } catch (e: Exception) {
                AppLogger.error("ControlLayout", "Failed to parse layout JSON", e)
                null
            }
        }

        /**
         * 修复控件可见性问题 (兼容旧版布局)
         * 
         * 旧版布局使用颜色自带的 alpha 通道控制透明度 (如 #4D000000 = 30% 黑色),
         * 新版代码新增了独立的 opacity 属性, 渲染时 paint.alpha = (opacity * 255)
         * 会完全覆盖颜色的 alpha。
         * 
         * 仅当 opacity / borderOpacity / textOpacity **全部**接近 0 时才执行修复,
         * 这表示是旧格式迁移问题。如果其中任意一个有意义的值 (>0.02),
         * 说明用户已经有意设置了各自的透明度 (例如 opacity=0 + borderOpacity=1
         * 表示"无背景只有边框"), 不应被篡改。
         */
        private fun sanitizeControls(layout: ControlLayout) {
            val minThreshold = 0.02f
            var fixedCount = 0

            // 收集所有需要检查的控件（共享 + 子布局中的）
            val allControls = layout.controls.toMutableList()
            layout.subLayouts.forEach { subLayout ->
                allControls.addAll(subLayout.controls)
            }

            for (control in allControls) {
                // 仅当所有三个透明度都接近 0 时才认为是旧格式需要迁移
                // 如果任意一个 > threshold，说明用户已有意设置，跳过
                val bgNearZero = control.opacity < minThreshold
                val borderNearZero = control.borderOpacity < minThreshold
                val textNearZero = control.textOpacity < minThreshold

                if (!bgNearZero && !borderNearZero && !textNearZero) {
                    continue // 所有值都正常，跳过
                }

                // 如果有任何一个值是有意义的，说明用户主动设置了透明度，不应修改
                if (!bgNearZero || !borderNearZero || !textNearZero) {
                    continue // 部分有值，说明是用户有意设计（如透明背景+可见边框），跳过
                }

                // 到这里: 三个透明度全部接近 0，属于旧格式迁移
                var fixed = false

                // 修复背景透明度: opacity ≈ 0 → 从 bgColor alpha 提取
                val bgAlpha = (control.bgColor ushr 24) / 255f
                if (bgAlpha > minThreshold) {
                    control.opacity = bgAlpha
                    AppLogger.debug("ControlLayout",
                        "  '${control.name}': opacity 0 → ${bgAlpha} (from bgColor alpha #${Integer.toHexString(control.bgColor ushr 24).uppercase()})")
                } else {
                    control.opacity = 0.35f
                    AppLogger.debug("ControlLayout",
                        "  '${control.name}': opacity 0 → 0.35 (default, bgColor alpha also 0)")
                }
                fixed = true

                // 修复边框透明度: borderOpacity ≈ 0 → 从 strokeColor alpha 提取
                val borderAlpha = (control.strokeColor ushr 24) / 255f
                if (borderAlpha > minThreshold) {
                    control.borderOpacity = borderAlpha
                } else {
                    control.borderOpacity = 0.5f
                }

                // 修复文本透明度: textOpacity ≈ 0 → 从 textColor alpha 提取
                val textAlpha = (control.textColor ushr 24) / 255f
                if (textAlpha > minThreshold) {
                    control.textOpacity = textAlpha
                } else {
                    control.textOpacity = 0.9f
                }

                if (fixed) fixedCount++
            }

            if (fixedCount > 0) {
                AppLogger.info("ControlLayout",
                    "Fixed $fixedCount controls with invisible opacity (in-memory only) in layout '${layout.name}'")
            }
        }
    }
}

