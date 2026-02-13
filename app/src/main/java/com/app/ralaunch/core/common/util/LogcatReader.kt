package com.app.ralaunch.core.common.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Logcat 读取器
 * 从 logcat 捕获日志并保存到文件
 */
class LogcatReader private constructor() {
    
    private var readerThread: Thread? = null
    private var logcatProcess: Process? = null
    private val running = AtomicBoolean(false)
    
    var logFile: File? = null
        private set
    private var logWriter: PrintWriter? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    private var callback: LogCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    interface LogCallback {
        fun onLogReceived(tag: String, level: String, message: String)
    }

    fun setCallback(callback: LogCallback?) {
        this.callback = callback
    }

    /**
     * 启动 logcat 读取
     */
    @JvmOverloads
    fun start(logDir: File?, filterTags: Array<String>? = null) {
        if (running.get()) {
            Log.w(TAG, "LogcatReader already running")
            return
        }

        try {
            logDir?.takeIf { !it.exists() }?.mkdirs()
            val fileName = "ralaunch_${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.log"
            logFile = File(logDir, fileName)
            logWriter = PrintWriter(FileWriter(logFile, true), true)
            Log.i(TAG, "LogcatReader started, logging to: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create log file", e)
            return
        }

        running.set(true)

        readerThread = Thread({
            try {
                Runtime.getRuntime().exec("logcat -c").waitFor()

                val cmd = buildString {
                    append("logcat -v time")
                    if (!filterTags.isNullOrEmpty()) {
                        append(" *:S")
                        filterTags.forEach { append(" $it:V") }
                    }
                }

                logcatProcess = Runtime.getRuntime().exec(cmd)
                BufferedReader(InputStreamReader(logcatProcess!!.inputStream)).use { reader ->
                    while (running.get()) {
                        val line = reader.readLine() ?: break
                        processLogLine(line)
                    }
                }
            } catch (e: Exception) {
                if (running.get()) {
                    Log.e(TAG, "LogcatReader error", e)
                }
            }
        }, "LogcatReader").apply {
            isDaemon = true
            start()
        }
    }

    private fun shouldFilterTag(tag: String?): Boolean {
        if (tag == null) return true
        return SYSTEM_TAG_BLACKLIST.any { tag.contains(it) }
    }

    private fun processLogLine(line: String) {
        if (line.isEmpty() || line.startsWith("---------")) return

        try {
            if (line.length < 18) return

            val timestamp = line.substring(0, 18).trim()
            var levelStart = 18
            while (levelStart < line.length && line[levelStart] == ' ') levelStart++
            if (levelStart >= line.length) return

            val tagStart = line.indexOf('/', levelStart)
            if (tagStart < 0) return

            val level = line.substring(levelStart, tagStart).trim()
            val messageStart = line.indexOf(':', tagStart)
            if (messageStart < 0) return

            val tagWithPid = line.substring(tagStart + 1, messageStart).trim()
            val tag = tagWithPid.substringBefore('(').trim()

            if (shouldFilterTag(tag)) return

            val message = if (messageStart + 1 < line.length) line.substring(messageStart + 1).trim() else ""
            val formattedLine = "[$timestamp] [$level] [$tag] $message"

            logWriter?.apply {
                println(formattedLine)
                flush()
            }

            callback?.let { cb ->
                mainHandler.post { cb.onLogReceived(tag, level, message) }
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        running.set(false)
        logcatProcess?.destroy()
        logcatProcess = null
        readerThread?.interrupt()
        readerThread = null
        logWriter?.apply { flush(); close() }
        logWriter = null
        Log.i(TAG, "LogcatReader stopped")
    }

    val isRunning: Boolean get() = running.get()

    companion object {
        private const val TAG = "LogcatReader"
        
        @Volatile
        private var instance: LogcatReader? = null

        @JvmStatic
        fun getInstance(): LogcatReader =
            instance ?: synchronized(this) {
                instance ?: LogcatReader().also { instance = it }
            }

        private val SYSTEM_TAG_BLACKLIST = arrayOf(
            "ScrollerOptimizationManager", "HWUI", "NativeTurboSchedManager",
            "TurboSchedMonitor", "MiuiMultiWindowUtils", "MiuiProcessManagerImpl",
            "FramePredict", "FirstFrameSpeedUp", "InsetsController", "ViewRootImpl",
            "Choreographer", "HandWritingStubImpl", "ViewRootImplStubImpl",
            "CompatChangeReporter", "ContentCatcher", "SecurityManager",
            "ComputilityLevel", "Activity", "libc", "SplineOverScroller",
            "BufferQueueProducer", "BLASTBufferQueue",
            "oplus.android.OplusFrameworkFactoryImpl", "DynamicFramerate",
            "OplusViewDragTouchViewHelper"
        )
    }
}
