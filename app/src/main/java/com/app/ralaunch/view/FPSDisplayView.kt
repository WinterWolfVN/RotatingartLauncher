package com.app.ralaunch.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.app.ralaunch.controls.bridges.SDLInputBridge
import com.app.ralaunch.data.SettingsManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * FPS 显示视图
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
        textSize = 48f
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(128, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private var currentFPS = 0f
    private var drawCalls = 0
    private var stateChanges = 0
    private var texChanges = 0
    private var cursorHidden = false
    private var showExtendedStats = false

    private val handler = Handler(Looper.getMainLooper())
    private var inputBridge: SDLInputBridge? = null
    private val settingsManager = SettingsManager.getInstance()

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

    // FPS 文本边界
    private var textLeft = 0f
    private var textTop = 0f
    private var textRight = 0f
    private var textBottom = 0f

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateFPSData()
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

    private fun updateFPSData() {
        try {
            Os.getenv("RALCORE_FPS")?.takeIf { it.isNotEmpty() }?.let {
                currentFPS = it.toFloatOrNull() ?: currentFPS
            }
            Os.getenv("RALCORE_CURSOR_HIDDEN")?.let { cursorHidden = it == "1" }
            Os.getenv("RALCORE_DRAWCALLS")?.takeIf { it.isNotEmpty() }?.let {
                drawCalls = it.toIntOrNull() ?: drawCalls
                showExtendedStats = true
            }
            Os.getenv("RALCORE_STATECHANGES")?.takeIf { it.isNotEmpty() }?.let {
                stateChanges = it.toIntOrNull() ?: stateChanges
            }
            Os.getenv("RALCORE_TEXCHANGES")?.takeIf { it.isNotEmpty() }?.let {
                texChanges = it.toIntOrNull() ?: texChanges
            }
        } catch (_: Exception) { }
        updateVisibility()
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

        val fpsText = if (currentFPS > 0) String.format("%.1f FPS", currentFPS) else "-- FPS"
        val extText = if (showExtendedStats && drawCalls > 0) "DC:$drawCalls TC:$texChanges" else null

        val textBounds = Rect()
        textPaint.getTextBounds(fpsText, 0, fpsText.length, textBounds)
        var textWidth = textPaint.measureText(fpsText)
        val textHeight = textBounds.height().toFloat()
        val padding = 10f

        extText?.let { textWidth = max(textWidth, textPaint.measureText(it)) }

        val baseX = if (fixedX >= 0) fixedX else 100f
        val baseY = if (fixedY >= 0) fixedY else 100f

        var textX = baseX + textWidth / 2 + padding
        var textY = baseY + textHeight + padding

        if (textX - textWidth / 2 < padding) textX = textWidth / 2 + padding
        else if (textX + textWidth / 2 > width - padding) textX = width - textWidth / 2 - padding

        if (textY - textHeight < padding) textY = textHeight + padding
        else if (textY > height - padding) textY = height.toFloat() - padding

        val bgLeft = textX - textWidth / 2 - padding
        val bgTop = textY - textHeight - padding
        val bgRight = textX + textWidth / 2 + padding
        var bgBottom = textY + padding

        if (extText != null) bgBottom += textHeight + 5

        canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, backgroundPaint)

        textLeft = bgLeft; textTop = bgTop; textRight = bgRight; textBottom = bgBottom

        canvas.drawText(fpsText, textX, textY, textPaint)

        extText?.let {
            val smallPaint = Paint(textPaint).apply {
                textSize = textPaint.textSize * 0.8f
                color = Color.rgb(200, 200, 200)
            }
            canvas.drawText(it, textX, textY + textHeight + 5, smallPaint)
        }
    }
}
