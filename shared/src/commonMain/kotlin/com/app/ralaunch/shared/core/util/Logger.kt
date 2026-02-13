package com.app.ralaunch.shared.core.util

/**
 * 跨平台日志接口
 * 
 * 各平台提供具体实现
 */
interface Logger {
    fun error(tag: String, message: String)
    fun error(tag: String, message: String, throwable: Throwable?)
    fun warn(tag: String, message: String)
    fun warn(tag: String, message: String, throwable: Throwable?)
    fun info(tag: String, message: String)
    fun debug(tag: String, message: String)
}

/**
 * 日志级别
 */
enum class LogLevel(val priority: Int, val label: String) {
    ERROR(0, "E"),
    WARN(1, "W"),
    INFO(2, "I"),
    DEBUG(3, "D"),
    VERBOSE(4, "V")
}

/**
 * 日志配置
 */
data class LogConfig(
    val enabled: Boolean = true,
    val minLevel: LogLevel = LogLevel.DEBUG,
    val enableFileLogging: Boolean = false,
    val logDirectory: String? = null
)

/**
 * 跨平台日志工具
 * 
 * 使用 expect/actual 模式获取平台实现
 */
expect object AppLog {
    fun error(tag: String, message: String)
    fun error(tag: String, message: String, throwable: Throwable?)
    fun warn(tag: String, message: String)
    fun info(tag: String, message: String)
    fun debug(tag: String, message: String)
}

/**
 * 日志扩展函数
 */
inline fun <reified T> T.logError(message: String) {
    AppLog.error(T::class.simpleName ?: "Unknown", message)
}

inline fun <reified T> T.logError(message: String, throwable: Throwable?) {
    AppLog.error(T::class.simpleName ?: "Unknown", message, throwable)
}

inline fun <reified T> T.logWarn(message: String) {
    AppLog.warn(T::class.simpleName ?: "Unknown", message)
}

inline fun <reified T> T.logInfo(message: String) {
    AppLog.info(T::class.simpleName ?: "Unknown", message)
}

inline fun <reified T> T.logDebug(message: String) {
    AppLog.debug(T::class.simpleName ?: "Unknown", message)
}
