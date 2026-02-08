package com.app.ralaunch.console

import com.app.ralaunch.utils.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 命令控制台管理器（单例）
 *
 * 收集实时日志、管理控制台状态、处理命令。
 */
object ConsoleManager {

    private const val TAG = "ConsoleManager"
    private const val MAX_LOG_LINES = 500
    private const val MAX_DEBUG_LOG_LINES = 30

    /** 只收集包含这些关键词的 tag（不区分大小写） */
    private val ALLOWED_TAG_KEYWORDS = listOf(
        "dotnet", "corehost", "gamelauncher", "processlauncher",
        "serverlauncher", "serverlaunch", "mono", "console",
        "tmodloader", "terraria", "fna", "sdl",
    )

    /** 日志条目 */
    data class LogEntry(
        val id: Long,
        val timestamp: String,
        val level: LogLevel,
        val tag: String,
        val message: String
    ) {
        val display: String get() = "[$timestamp] [$level/$tag] $message"
    }

    private var nextId = java.util.concurrent.atomic.AtomicLong(0)

    enum class LogLevel { V, D, I, W, E }

    // 全部日志
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    // 最近日志（用于调试日志覆盖层）
    private val _recentLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val recentLogs: StateFlow<List<LogEntry>> = _recentLogs.asStateFlow()

    // 控制台可见性
    private val _consoleVisible = MutableStateFlow(false)
    val consoleVisible: StateFlow<Boolean> = _consoleVisible.asStateFlow()

    // 调试日志可见性
    private val _debugLogVisible = MutableStateFlow(false)
    val debugLogVisible: StateFlow<Boolean> = _debugLogVisible.asStateFlow()

    private val logBuffer = CopyOnWriteArrayList<LogEntry>()
    private var logcatThread: Thread? = null
    private var isRunning = false
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /**
     * 开始收集日志
     */
    fun start() {
        if (isRunning) return
        isRunning = true

        logcatThread = Thread({
            try {
                // 使用 tag 格式：X/Tag: message，不清除旧缓冲
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-v", "tag", "-T", "100"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (isRunning) {
                    line = reader.readLine()
                    if (line != null && line.isNotBlank()) {
                        val entry = parseLine(line)
                        if (entry != null) {
                            addLog(entry)
                        }
                    }
                }
                process.destroy()
            } catch (e: Exception) {
                if (isRunning) {
                    AppLogger.error(TAG, "日志收集异常: ${e.message}")
                }
            }
        }, "ConsoleLogCollector").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * 停止收集日志
     */
    fun stop() {
        isRunning = false
        logcatThread?.interrupt()
        logcatThread = null
    }

    /**
     * 手动添加一条日志
     */
    fun addLog(entry: LogEntry) {
        logBuffer.add(entry)
        // 裁剪缓冲
        while (logBuffer.size > MAX_LOG_LINES) {
            logBuffer.removeAt(0)
        }
        _logs.value = logBuffer.toList()
        _recentLogs.value = logBuffer.takeLast(MAX_DEBUG_LOG_LINES)

        // 智能提示：检测服务器输出并给出操作提示
        if (entry.tag != HINT_TAG) {
            checkAndShowHint(entry.message)
        }
    }

    /**
     * 添加一条控制台消息
     */
    fun addMessage(message: String, level: LogLevel = LogLevel.I) {
        addLog(LogEntry(
            id = nextId.getAndIncrement(),
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = "Console",
            message = message
        ))
    }

    // ==================== 智能提示 ====================

    private const val HINT_TAG = "💡 提示"

    /** 避免同一提示短时间内重复显示 */
    private var lastHintKey = ""
    private var lastHintTime = 0L
    private const val HINT_COOLDOWN_MS = 5000L

    private fun addHint(key: String, message: String) {
        val now = System.currentTimeMillis()
        if (key == lastHintKey && now - lastHintTime < HINT_COOLDOWN_MS) return
        lastHintKey = key
        lastHintTime = now
        addLog(LogEntry(
            id = nextId.getAndIncrement(),
            timestamp = timeFormat.format(Date()),
            level = LogLevel.W,
            tag = HINT_TAG,
            message = message
        ))
    }

    /**
     * 检测服务器输出的关键内容，自动添加操作提示
     */
    private fun checkAndShowHint(msg: String) {
        val m = msg.trim().lowercase()
        when {
            // 世界选择菜单
            m.contains("new world") && m.contains("n") ->
                addHint("world_select", " 输入数字选择1,2,3,4选择世界，输入 n 创建新世界，输入 d+数字 删除世界")

            // 端口输入
            m.contains("server port") || (m.contains("port") && m.contains("7777")) ->
                addHint("port", "⬇ 输入端口号（默认 7777，直接回车使用默认值）")

            // 最大玩家数
            m.contains("max player") || m.contains("maxplayers") ->
                addHint("maxplayers", "⬇ 输入最大玩家数（直接回车使用默认值）")

            // 密码
            m.contains("server password") ->
                addHint("password", "⬇ 输入服务器密码（留空则无密码，直接回车跳过）")

            // 服务器启动成功
            m.contains("listening on port") || m.contains("server started") -> {
                // 尝试提取端口号
                val portMatch = Regex("""port\s*:?\s*(\d+)""", RegexOption.IGNORE_CASE).find(msg)
                val port = portMatch?.groupValues?.get(1) ?: "7777"
                addHint("server_ready",
                    " 服务器已启动！游戏内连接方式: 多人模式 → 通过IP加入 → 127.0.0.1:$port")
            }

            // 自动转发
            m.contains("auto-forwarding port") || m.contains("upnp") ->
                addHint("upnp", " 正在尝试 UPnP 端口转发，外网玩家可通过你的公网IP连接")

            // Mods 加载
            m.contains("loading mods") || m.contains("loading mod") ->
                addHint("mods_loading", " 正在加载 Mods，请耐心等待...")

            // 世界生成中
            m.contains("generating world") || m.contains("world generation") ->
                addHint("worldgen", " 正在生成新世界，这可能需要几分钟...")

            // 世界保存
            m.contains("saving world") ->
                addHint("saving", " 正在保存世界...")
        }
    }

    fun toggleConsole() {
        _consoleVisible.value = !_consoleVisible.value
    }

    fun setConsoleVisible(visible: Boolean) {
        _consoleVisible.value = visible
    }

    fun toggleDebugLog() {
        _debugLogVisible.value = !_debugLogVisible.value
    }

    fun setDebugLogVisible(visible: Boolean) {
        _debugLogVisible.value = visible
    }

    fun clearLogs() {
        logBuffer.clear()
        _logs.value = emptyList()
        _recentLogs.value = emptyList()
    }

    // 预编译正则：tag 格式 "X/Tag: message" 或 brief 格式 "X/Tag( PID): message"
    private val tagRegex = Regex("""^([VDIWEFS])/(.+?):\s*(.*)$""")
    private val briefRegex = Regex("""^([VDIWEFS])/(.+?)\(\s*\d+\):\s*(.*)$""")

    /**
     * 解析 logcat 行（仅保留 DOTNET 相关 tag）
     */
    private fun parseLine(line: String): LogEntry? {
        val match = tagRegex.find(line) ?: briefRegex.find(line) ?: return null
        val levelChar = match.groupValues[1]
        val tag = match.groupValues[2].trim()
        val msg = match.groupValues[3]

        // 过滤：只保留 DOTNET 相关的 tag
        val tagLower = tag.lowercase()
        if (ALLOWED_TAG_KEYWORDS.none { tagLower.contains(it) }) return null

        val level = when (levelChar) {
            "V" -> LogLevel.V
            "D" -> LogLevel.D
            "I" -> LogLevel.I
            "W" -> LogLevel.W
            "E", "F", "S" -> LogLevel.E
            else -> LogLevel.I
        }
        return LogEntry(
            id = nextId.getAndIncrement(),
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = msg
        )
    }
}
