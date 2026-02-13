package com.app.ralaunch.shared.core.contract.service

import com.app.ralaunch.shared.core.model.domain.GameItem
import kotlinx.coroutines.flow.Flow

/**
 * 游戏启动结果
 */
sealed class LaunchResult {
    data class Success(val exitCode: Int) : LaunchResult()
    data class Error(val message: String, val exception: Throwable? = null) : LaunchResult()
    object Cancelled : LaunchResult()
}

/**
 * 游戏启动状态
 */
sealed class LaunchState {
    object Idle : LaunchState()
    data class Preparing(val message: String) : LaunchState()
    data class Launching(val game: GameItem) : LaunchState()
    data class Running(val game: GameItem) : LaunchState()
    data class Finished(val result: LaunchResult) : LaunchState()
}

/**
 * 游戏启动配置
 */
data class LaunchConfig(
    val enablePatches: Boolean = true,
    val enableModLoader: Boolean = false,
    val customArgs: List<String> = emptyList(),
    val environmentVariables: Map<String, String> = emptyMap()
)

/**
 * 游戏启动服务接口
 * 
 * 定义跨平台的游戏启动抽象，实际实现由平台层提供
 */
interface GameLaunchService {
    
    /**
     * 当前启动状态
     */
    val launchState: Flow<LaunchState>
    
    /**
     * 检查游戏是否可以启动
     * @param game 游戏信息
     * @return 如果可以启动返回 null，否则返回错误信息
     */
    suspend fun canLaunch(game: GameItem): String?
    
    /**
     * 启动游戏
     * @param game 游戏信息
     * @param config 启动配置
     * @return 启动结果
     */
    suspend fun launch(game: GameItem, config: LaunchConfig = LaunchConfig()): LaunchResult
    
    /**
     * 停止当前运行的游戏
     */
    suspend fun stop()
    
    /**
     * 获取上次启动的错误信息
     */
    fun getLastError(): String?
    
    /**
     * 检查运行时是否已安装
     * @param runtime 运行时类型
     */
    suspend fun isRuntimeInstalled(runtime: String): Boolean
    
    /**
     * 初始化运行时环境
     * @param runtime 运行时类型
     * @return 是否初始化成功
     */
    suspend fun initializeRuntime(runtime: String): Boolean
}

/**
 * 运行时类型常量
 */
object RuntimeTypes {
    const val DOTNET = "dotnet"
    const val BOX64 = "box64"
    const val NATIVE = "native"
}
