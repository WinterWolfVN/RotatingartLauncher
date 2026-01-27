package com.app.ralaunch.shared.data.model

import kotlinx.serialization.Serializable

/**
 * 游戏项数据模型 - 跨平台共享
 */
@Serializable
data class GameItem(
    val gameName: String,
    val assemblyPath: String,
    val gameDescription: String? = null,
    val iconResId: Int = 0,
    val iconPath: String? = null,
    val runtime: String? = null,
    val isShortcut: Boolean = false,
    val enabledPatchIds: List<String> = emptyList()
) {
    companion object {
        /**
         * 从 GameDefinition 创建 GameItem
         */
        fun fromDefinition(
            definition: GameDefinition,
            assemblyPath: String,
            iconPath: String? = null
        ): GameItem {
            return GameItem(
                gameName = definition.displayName,
                assemblyPath = assemblyPath,
                gameDescription = null,
                iconResId = 0,
                iconPath = iconPath,
                runtime = if (definition.runtime == "dotnet") null else definition.runtime,
                isShortcut = false,
                enabledPatchIds = emptyList()
            )
        }
    }
}

/**
 * 游戏定义 - 跨平台共享
 * 集中定义游戏的元信息，供插件和 GameItem 使用
 */
@Serializable
data class GameDefinition(
    /** 游戏唯一标识，如 "terraria", "tmodloader", "celeste" */
    val id: String,
    
    /** 显示名称，如 "Terraria", "tModLoader", "Celeste" */
    val displayName: String,
    
    /** 启动目标文件，如 "Terraria.exe", "tModLoader.dll" */
    val launchTarget: String,
    
    /** 运行时类型: "dotnet" 或 "box64" */
    val runtime: String = "dotnet",
    
    /** 图标搜索模式，用于提取图标时匹配文件名 */
    val iconPatterns: List<String> = emptyList(),
    
    /** 是否为 ModLoader */
    val isModLoader: Boolean = false,
    
    /** 默认渲染器 ID，null 表示使用全局设置 */
    val defaultRenderer: String? = null
) {
    companion object {
        // ==================== Terraria 系列 ====================
        val TERRARIA = GameDefinition(
            id = "terraria",
            displayName = "Terraria",
            launchTarget = "Terraria.exe",
            iconPatterns = listOf("terraria")
        )
        
        val TMODLOADER = GameDefinition(
            id = "tmodloader",
            displayName = "tModLoader",
            launchTarget = "tModLoader.dll",
            iconPatterns = listOf("tmodloader", "terraria"),
            isModLoader = true
        )
        
        // ==================== Celeste 系列 ====================
        val CELESTE = GameDefinition(
            id = "celeste",
            displayName = "Celeste",
            launchTarget = "Celeste.exe",
            iconPatterns = listOf("celeste")
        )
        
        val EVEREST = GameDefinition(
            id = "everest",
            displayName = "Everest",
            launchTarget = "Celeste.dll",
            iconPatterns = listOf("celeste"),
            isModLoader = true
        )
        
        // ==================== Stardew Valley 系列 ====================
        val STARDEW_VALLEY = GameDefinition(
            id = "stardew_valley",
            displayName = "Stardew Valley",
            launchTarget = "Stardew Valley.exe",
            iconPatterns = listOf("stardew"),
            defaultRenderer = "gl4es"  // 星露谷默认使用 GL4ES 渲染器
        )
        
        val SMAPI = GameDefinition(
            id = "smapi",
            displayName = "SMAPI",
            launchTarget = "StardewModdingAPI.dll",
            iconPatterns = listOf("stardew"),
            isModLoader = true,
            defaultRenderer = "gl4es"  // SMAPI 默认使用 GL4ES 渲染器
        )
        
        // ==================== Starbound ====================
        val STARBOUND = GameDefinition(
            id = "starbound",
            displayName = "Starbound",
            launchTarget = "starbound",
            runtime = "box64",
            iconPatterns = listOf("starbound")
        )
        
        // ==================== Don't Starve 系列 ====================
        val DONT_STARVE = GameDefinition(
            id = "dont_starve",
            displayName = "Don't Starve",
            launchTarget = "dontstarve",
            runtime = "box64",
            iconPatterns = listOf("dontstarve", "dont_starve")
        )
        
        val DONT_STARVE_TOGETHER = GameDefinition(
            id = "dst",
            displayName = "Don't Starve Together",
            launchTarget = "dontstarve_dedicated_server_nullrenderer",
            runtime = "box64",
            iconPatterns = listOf("dontstarve", "dst")
        )
    }
}
