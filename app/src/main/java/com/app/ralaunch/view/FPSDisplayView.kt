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
        private const val UPDATE_INTERVAL = 500L
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
                val line = reader.readLine() ?: return
                if (!line.startsWith("cpu ")) return
                
                val parts = line.split("\\s+".toRegex())
                if (parts.size < 5) return
                
                // user + nice + system + idle + iowait + irq + softirq
                val user = parts[1].toLongOrNull() ?: 0L
                val nice = parts[2].toLongOrNull() ?: 0L
                val system = parts[3].toLongOrNull() ?: 0L
                val idle = parts[4].toLongOrNull() ?: 0L
                val iowait = if (parts.size > 5) parts[5].toLongOrNull() ?: 0L else 0L
                val irq = if (parts.size > 6) parts[6].toLongOrNull() ?: 0L else 0L
                val softirq = if (parts.size > 7) parts[7].toLongOrNull() ?: 0L else 0L
                
                val total = user + nice + system + idle + iowait + irq + softirq
                val idleTime = idle + iowait
                
                if (lastCpuTotal > 0) {
                    val totalDiff = total - lastCpuTotal
                    val idleDiff = idleTime - lastCpuIdle
                    if (totalDiff > 0) {
                        cpuUsage = ((totalDiff - idleDiff).toFloat() / totalDiff.toFloat()) * 100f
                    }
                }
                
                lastCpuTotal = total
                lastCpuIdle = idleTime
            }
        } catch (_: Exception) {
            cpuUsage = 0f
        }
    }

    /** 更新 GPU 使用率 (从 sysfs 读取，支持 Adreno/Mali) */
    private fun updateGpuUsage() {
        try {
            // Adreno GPU (Qualcomm)
            val adrenoPath = "/sys/class/kgsl/kgsl-3d0/gpubusy"
            val maliPath = "/sys/class/devfreq/gpufreq/cur_freq"
            val maliLoadPath = "/sys/kernel/gpu/gpu_busy"
            
            when {
                java.io.File(adrenoPath).exists() -> {
                    BufferedReader(FileReader(adrenoPath)).use { reader ->
                        val line = reader.readLine() ?: return
                        val parts = line.split("\\s+".toRegex())
                        if (parts.size >= 2) {
                            val busy = parts[0].toLongOrNull() ?: 0L
                            val total = parts[1].toLongOrNull() ?: 1L
                            if (total > 0) {
                                gpuUsage = (busy.toFloat() / total.toFloat()) * 100f
                            }
                        }
                    }
                }
                java.io.File(maliLoadPath).exists() -> {
                    BufferedReader(FileReader(maliLoadPath)).use { reader ->
                        val line = reader.readLine() ?: return
                        gpuUsage = line.trim().replace("%", "").toFloatOrNull() ?: 0f
                    }
                }
                else -> {
                    gpuUsage = 0f
                }
            }
        } catch (_: Exception) {
            gpuUsage = 0f
        }
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
        // CPU 和 GPU 使用率
        val usageText = buildString {
            append("CPU: ")
            append(if (cpuUsage > 0) String.format("%.0f%%", cpuUsage) else "--%")
            append("  GPU: ")
            append(if (gpuUsage > 0) String.format("%.0f%%", gpuUsage) else "--%")
        }
        lines.add(usageText)
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
