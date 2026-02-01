package com.app.ralaunch.view

import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.app.ralaunch.controls.bridges.SDLInputBridge
import com.app.ralaunch.data.SettingsManager
import java.io.BufferedReader
import java.io.FileReader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * FPS 显示视图
 * 从 SDL 底层获取 FPS，显示 GPU/CPU/RAM 信息
 */
class FPSDisplayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val UPDATE_INTERVAL = 200L  // 更频繁更新 FPS 显示
        private const val DRAG_THRESHOLD = 10f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private var currentFPS = 0f
    private var frameTimeMs = 0f
    private var cpuUsage = 0f
    private var gpuUsage = 0f
    private var ramUsage = ""
    
    // CPU 使用率计算
    private var lastCpuTotal = 0L
    private var lastCpuIdle = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var inputBridge: SDLInputBridge? = null
    private val settingsManager = SettingsManager.getInstance()
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    // 拖动相关
    private var lastX = 0f
    private var lastY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialViewX = 0f
    private var initialViewY = 0f
    private var isDragging = false
    private var isTrackingTouch = false

    // 固定位置
    private var fixedX = settingsManager.fpsDisplayX
    private var fixedY = settingsManager.fpsDisplayY

    // 文本边界
    private var textLeft = 0f
    private var textTop = 0f
    private var textRight = 0f
    private var textBottom = 0f

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateData()
            invalidate()
            handler.postDelayed(this, UPDATE_INTERVAL)
        }
    }

    init {
        visibility = GONE
    }

    fun setInputBridge(bridge: SDLInputBridge?) {
        inputBridge = bridge
    }

    fun start() {
        updateVisibility()
        handler.post(updateRunnable)   
    }

    fun stop() {
        handler.removeCallbacks(updateRunnable)
    }

    fun refreshVisibility() {
        updateVisibility()
        invalidate()
    }

    /** 更新所有数据 */
    private fun updateData() {
        try {
            // 从 SDL 底层读取 FPS（滑动窗口平均）
            Os.getenv("RAL_FPS")?.takeIf { it.isNotEmpty() }?.let {
                currentFPS = it.toFloatOrNull() ?: currentFPS
            }
            // 从 SDL 底层读取帧时间
            Os.getenv("RAL_FRAME_TIME")?.takeIf { it.isNotEmpty() }?.let {
                frameTimeMs = it.toFloatOrNull() ?: frameTimeMs
            }
        } catch (_: Exception) { }
        
        // 更新 CPU/GPU/RAM 使用率
        updateCpuUsage()
        updateGpuUsage()
        updateRamUsage()
        updateVisibility()
    }

    /** 更新 CPU 使用率 (从 /proc/stat 读取) */
    private fun updateCpuUsage() {
        try {
            BufferedReader(FileReader("/proc/stat")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.startsWith("cpu ")) {
                        val parts = line!!.split("\\s+".toRegex())
                        if (parts.size >= 5) {
                            // user + nice + system + idle + iowait + irq + softirq + steal + guest + guest_nice
                            val user = parts[1].toLongOrNull() ?: 0L
                            val nice = parts[2].toLongOrNull() ?: 0L
                            val system = parts[3].toLongOrNull() ?: 0L
                            val idle = parts[4].toLongOrNull() ?: 0L
                            val iowait = if (parts.size > 5) parts[5].toLongOrNull() ?: 0L else 0L
                            val irq = if (parts.size > 6) parts[6].toLongOrNull() ?: 0L else 0L
                            val softirq = if (parts.size > 7) parts[7].toLongOrNull() ?: 0L else 0L
                            val steal = if (parts.size > 8) parts[8].toLongOrNull() ?: 0L else 0L
                            
                            val total = user + nice + system + idle + iowait + irq + softirq + steal
                            val idleTime = idle + iowait
                            
                            if (lastCpuTotal > 0 && total > lastCpuTotal) {
                                val totalDiff = total - lastCpuTotal
                                val idleDiff = idleTime - lastCpuIdle
                                if (totalDiff > 0) {
                                    cpuUsage = ((totalDiff - idleDiff).toFloat() / totalDiff.toFloat()) * 100f
                                    cpuUsage = cpuUsage.coerceIn(0f, 100f)
                                }
                            } else if (lastCpuTotal == 0L) {
                                // 首次读取，初始化但不计算
                                lastCpuTotal = total
                                lastCpuIdle = idleTime
                                return@use
                            }
                            
                            lastCpuTotal = total
                            lastCpuIdle = idleTime
                        }
                        break
                    }
                }
            }
        } catch (e: Exception) {
            // 备用方案：尝试读取当前进程的 CPU 时间
            try {
                val pid = android.os.Process.myPid()
                BufferedReader(FileReader("/proc/$pid/stat")).use { reader ->
                    val line = reader.readLine()
                    if (line != null) {
                        val parts = line.split("\\s+".toRegex())
                        if (parts.size > 14) {
                            val utime = parts[13].toLongOrNull() ?: 0L
                            val stime = parts[14].toLongOrNull() ?: 0L
                            val processTime = utime + stime
                            // 这里只能显示进程相对 CPU 时间，不是百分比
                            // 但至少能显示有活动
                            if (processTime > 0 && cpuUsage <= 0f) {
                                cpuUsage = 1f // 标记为有活动
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    /** 更新 GPU 使用率 (从 sysfs 读取，支持多种 GPU) */
    private fun updateGpuUsage() {
        // 尝试多种路径 - 按常见程度排序
        val gpuPaths = listOf(
            // Adreno GPU (Qualcomm) - 最常见
            "/sys/class/kgsl/kgsl-3d0/gpubusy" to ::parseAdrenoGpuBusy,
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage" to ::parsePercentage,
            "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load" to ::parsePercentage,
            
            // Mali GPU (ARM) - 三星、华为、联发科等
            "/sys/kernel/gpu/gpu_busy" to ::parsePercentage,
            "/sys/class/misc/mali0/device/utilization" to ::parsePercentage,
            "/sys/devices/platform/mali.0/utilization" to ::parsePercentage,
            "/sys/devices/platform/e82c0000.mali/utilization" to ::parsePercentage,
            "/sys/devices/platform/13000000.mali/utilization" to ::parsePercentage,
            "/sys/devices/platform/18500000.mali/utilization" to ::parsePercentage,
            "/sys/devices/platform/1c500000.mali/utilization" to ::parsePercentage,
            
            // 联发科 (MediaTek) GPU
            "/sys/kernel/ged/hal/gpu_utilization" to ::parsePercentage,
            "/sys/kernel/ged/hal/gpu_util" to ::parsePercentage,
            "/d/ged/hal/gpu_utilization" to ::parsePercentage,
            "/sys/module/ged/parameters/gpu_loading" to ::parsePercentage,
            "/proc/gpu_utilization" to ::parsePercentage,
            "/proc/gpufreq/gpufreq_var_dump" to ::parseMtkGpuDump,
            
            // 华为麒麟 (Kirin) GPU
            "/sys/class/devfreq/gpufreq/cur_freq" to ::parseKirinGpu,
            "/sys/class/devfreq/ffa30000.gpu/load" to ::parsePercentage,
            "/sys/class/devfreq/e82c0000.mali/load" to ::parsePercentage,
            
            // Exynos (三星)
            "/sys/kernel/gpu/gpu_busy" to ::parsePercentage,
            "/sys/devices/platform/17500000.g3d/utilization" to ::parsePercentage,
            "/sys/devices/platform/18500000.g3d/utilization" to ::parsePercentage,
            
            // PowerVR GPU (联发科旧款)
            "/sys/kernel/debug/pvr/status" to ::parsePvrStatus,
            
            // 通用 devfreq 路径
            "/sys/class/devfreq/gpufreq/load" to ::parsePercentage,
            "/sys/class/devfreq/13000000.gpu/load" to ::parsePercentage,
            "/sys/class/devfreq/soc:qcom,kgsl-busmon/cur_freq" to ::parsePercentage,
        )
        
        for ((path, parser) in gpuPaths) {
            try {
                val file = java.io.File(path)
                if (file.exists() && file.canRead()) {
                    BufferedReader(FileReader(path)).use { reader ->
                        val line = reader.readLine()
                        if (line != null) {
                            val result = parser(line)
                            if (result >= 0f) {
                                gpuUsage = result.coerceIn(0f, 100f)
                                return
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        
        // 尝试动态查找 devfreq 下的 GPU
        tryFindDevfreqGpu()
    }
    
    /** 动态查找 devfreq 下的 GPU 设备 */
    private fun tryFindDevfreqGpu() {
        try {
            val devfreqDir = java.io.File("/sys/class/devfreq")
            if (devfreqDir.exists() && devfreqDir.isDirectory) {
                devfreqDir.listFiles()?.forEach { device ->
                    val name = device.name.lowercase()
                    if (name.contains("gpu") || name.contains("mali") || name.contains("kgsl") || name.contains("g3d")) {
                        // 尝试读取 load 或 utilization
                        listOf("load", "cur_freq", "trans_stat").forEach { attr ->
                            val attrFile = java.io.File(device, attr)
                            if (attrFile.exists() && attrFile.canRead()) {
                                try {
                                    val content = attrFile.readText().trim()
                                    val value = parsePercentage(content)
                                    if (value >= 0f) {
                                        gpuUsage = value.coerceIn(0f, 100f)
                                        return
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) { }
    }
    
    private fun parseAdrenoGpuBusy(line: String): Float {
        val parts = line.split("\\s+".toRegex())
        if (parts.size >= 2) {
            val busy = parts[0].toLongOrNull() ?: return -1f
            val total = parts[1].toLongOrNull() ?: return -1f
            if (total > 0) {
                return (busy.toFloat() / total.toFloat()) * 100f
            }
        }
        return -1f
    }
    
    private fun parsePercentage(line: String): Float {
        val cleaned = line.trim().replace("%", "").replace("@", " ").split("\\s+".toRegex())[0]
        return cleaned.toFloatOrNull() ?: -1f
    }
    
    private fun parsePvrStatus(line: String): Float {
        // PowerVR 格式: "GPU Utilization: XX%"
        val match = Regex("(\\d+)%").find(line)
        return match?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
    }
    
    /** 解析联发科 GPU dump 格式 */
    private fun parseMtkGpuDump(line: String): Float {
        // 联发科格式可能是 "gpu_loading: XX" 或 "loading=XX"
        val patterns = listOf(
            Regex("loading[=:]\\s*(\\d+)"),
            Regex("util[=:]\\s*(\\d+)"),
            Regex("(\\d+)\\s*%")
        )
        for (pattern in patterns) {
            val match = pattern.find(line)
            if (match != null) {
                return match.groupValues[1].toFloatOrNull() ?: -1f
            }
        }
        return -1f
    }
    
    /** 解析华为 Kirin GPU (通过频率估算负载) */
    private fun parseKirinGpu(line: String): Float {
        // Kirin 可能只提供频率，无法直接获取使用率
        // 返回 -1 表示需要其他方法
        return -1f
    }

    /** 更新 RAM 使用 */
    private fun updateRamUsage() {
        try {
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val usedMB = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024)
            val totalMB = memInfo.totalMem / (1024 * 1024)
            ramUsage = "${usedMB}/${totalMB}MB"
        } catch (_: Exception) {
            ramUsage = ""
        }
    }

    private fun updateVisibility() {
        visibility = if (settingsManager.isFPSDisplayEnabled) VISIBLE else GONE
    }

    private fun isTouchInTextArea(touchX: Float, touchY: Float): Boolean {
        if (textRight <= textLeft || textBottom <= textTop) return false
        val padding = 30f
        return touchX >= textLeft - padding && touchX <= textRight + padding &&
               touchY >= textTop - padding && touchY <= textBottom + padding
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val touchX = event.x
        val touchY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!isTouchInTextArea(touchX, touchY)) {
                    isTrackingTouch = false
                    return false
                }
                isTrackingTouch = true
                lastX = event.rawX
                lastY = event.rawY
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                initialViewX = if (fixedX >= 0) fixedX else 100f
                initialViewY = if (fixedY >= 0) fixedY else 100f
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isTrackingTouch) return false
                val deltaX = event.rawX - lastX
                val deltaY = event.rawY - lastY
                val distance = sqrt(deltaX * deltaX + deltaY * deltaY)

                if (distance > DRAG_THRESHOLD || isDragging) {
                    if (!isDragging) {
                        isDragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    val margin = 50f
                    fixedX = max(margin, min(initialViewX + (event.rawX - initialTouchX), width - margin))
                    fixedY = max(margin, min(initialViewY + (event.rawY - initialTouchY), height - margin))
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isTrackingTouch) return false
                isTrackingTouch = false
                if (isDragging) {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    settingsManager.fpsDisplayX = fixedX
                    settingsManager.fpsDisplayY = fixedY
                }
                return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!settingsManager.isFPSDisplayEnabled) return

        // FPS 和帧时间显示
        val fpsText = if (currentFPS > 0) {
            if (frameTimeMs > 0) {
                String.format("%.1f FPS (%.1fms)", currentFPS, frameTimeMs)
            } else {
                String.format("%.1f FPS", currentFPS)
            }
        } else "-- FPS"
        
        // 构建显示信息行
        val lines = mutableListOf(fpsText)
        // CPU 和 GPU 使用率 - 始终显示百分比
        val cpuStr = String.format("%.0f%%", cpuUsage)
        val gpuStr = String.format("%.0f%%", gpuUsage)
        lines.add("CPU: $cpuStr  GPU: $gpuStr")
        if (ramUsage.isNotEmpty()) lines.add("RAM: $ramUsage")

        val textBounds = Rect()
        textPaint.getTextBounds(fpsText, 0, fpsText.length, textBounds)
        val lineHeight = textBounds.height().toFloat() + 8f
        val padding = 12f

        // 计算最大宽度
        var maxWidth = 0f
        val smallPaint = Paint(textPaint).apply { textSize = textPaint.textSize * 0.85f }
        maxWidth = textPaint.measureText(fpsText)
        for (i in 1 until lines.size) {
            maxWidth = max(maxWidth, smallPaint.measureText(lines[i]))
        }

        val baseX = if (fixedX >= 0) fixedX else 100f
        val baseY = if (fixedY >= 0) fixedY else 100f

        var textX = baseX + maxWidth / 2 + padding
        var textY = baseY + lineHeight

        if (textX - maxWidth / 2 < padding) textX = maxWidth / 2 + padding
        else if (textX + maxWidth / 2 > width - padding) textX = width - maxWidth / 2 - padding

        if (textY < padding + lineHeight) textY = padding + lineHeight
        else if (textY + lineHeight * lines.size > height - padding) 
            textY = height - padding - lineHeight * lines.size

        val bgLeft = textX - maxWidth / 2 - padding
        val bgTop = textY - lineHeight
        val bgRight = textX + maxWidth / 2 + padding
        val bgBottom = textY + lineHeight * (lines.size - 1) + padding

        canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, backgroundPaint)

        textLeft = bgLeft; textTop = bgTop; textRight = bgRight; textBottom = bgBottom

        // 绘制 FPS（主文字）
        canvas.drawText(fpsText, textX, textY, textPaint)

        // 绘制其他信息（小号灰色文字）
        smallPaint.color = Color.rgb(200, 200, 200)
        for (i in 1 until lines.size) {
            canvas.drawText(lines[i], textX, textY + lineHeight * i, smallPaint)
        }
    }
}
