package com.app.ralaunch.controls.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.app.ralaunch.controls.bridges.ControlInputBridge
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.textures.TextureLoader
import com.app.ralaunch.manager.VibrationManager
import org.koin.java.KoinJavaComponent
import java.io.File
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 十字键控件
 * 
 * 支持上下左右四个方向，可选支持斜向输入
 */
class VirtualDPad(
    context: Context?,
    data: ControlData,
    private val mInputBridge: ControlInputBridge
) : View(context), ControlView {

    companion object {
        private const val TAG = "VirtualDPad"
        
        // 方向常量
        const val DIR_NONE = 0
        const val DIR_UP = 1
        const val DIR_DOWN = 2
        const val DIR_LEFT = 4
        const val DIR_RIGHT = 8
    }

    // 震动管理器
    private val vibrationManager: VibrationManager? by lazy {
        try {
            KoinJavaComponent.get(VibrationManager::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "VibrationManager not available: ${e.message}")
            null
        }
    }

    override var controlData: ControlData = data
        set(value) {
            field = value
            initPaints()
            invalidate()
        }

    private val castedData: ControlData.DPad
        get() = controlData as ControlData.DPad

    // 纹理相关
    private var textureLoader: TextureLoader? = null
    private var assetsDir: File? = null

    override fun setPackAssetsDir(dir: File?) {
        assetsDir = dir
        if (dir != null && textureLoader == null) {
            textureLoader = TextureLoader.getInstance(context)
        }
        invalidate()
    }

    // 绘制相关
    private lateinit var mBackgroundPaint: Paint
    private lateinit var mButtonPaint: Paint
    private lateinit var mActivePaint: Paint
    private lateinit var mStrokePaint: Paint
    private lateinit var mTextPaint: TextPaint

    // 按钮区域
    private val upRect = RectF()
    private val downRect = RectF()
    private val leftRect = RectF()
    private val rightRect = RectF()
    private val centerRect = RectF()

    // 当前激活的方向
    private var activeDirections = DIR_NONE
    private var previousDirections = DIR_NONE

    // 触摸相关
    private var mTouchPointerId = -1

    init {
        initPaints()
    }

    private fun initPaints() {
        val opacity = controlData.opacity
        val borderOpacity = controlData.borderOpacity
        
        // 自动检测：如果边框是透明的或宽度为0，根据背景亮度自动设置边框颜色
        val bgLuminance = Color.luminance(controlData.bgColor)
        val isLightBg = bgLuminance > 0.5f
        
        // 如果用户没有设置边框颜色（透明）或宽度为0，自动计算
        val userStrokeColor = controlData.strokeColor
        val hasUserStroke = (userStrokeColor ushr 24) > 10 && controlData.strokeWidth > 0
        val effectiveStrokeColor = if (hasUserStroke) {
            userStrokeColor
        } else {
            // 根据背景亮度选择对比色
            if (isLightBg) Color.parseColor("#333333") else Color.WHITE
        }
        val effectiveStrokeWidth = if (controlData.strokeWidth > 0) {
            controlData.strokeWidth
        } else {
            2f // 默认 2dp 边框
        }
        val effectiveBorderOpacity = if (borderOpacity > 0.01f) borderOpacity else 0.5f

        mBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = controlData.bgColor
            alpha = (opacity * 255).toInt()
            style = Paint.Style.FILL
        }

        mButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = controlData.bgColor
            alpha = (opacity * 255).toInt()
            style = Paint.Style.FILL
        }

        mActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = castedData.activeColor
            alpha = (opacity * 255).toInt().coerceAtLeast(100)
            style = Paint.Style.FILL
        }

        mStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = effectiveStrokeColor
            alpha = (effectiveBorderOpacity * 255).toInt()
            style = Paint.Style.STROKE
            strokeWidth = effectiveStrokeWidth * resources.displayMetrics.density
        }

        mTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = controlData.textColor
            alpha = (controlData.textOpacity * 255).toInt()
            textSize = min(width, height) * 0.15f
            textAlign = Paint.Align.CENTER
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateButtonRects()
        mTextPaint.textSize = min(w, h) * 0.12f
    }

    private fun updateButtonRects() {
        val w = width.toFloat()
        val h = height.toFloat()
        val size = min(w, h)
        
        val buttonSize = size * castedData.buttonSize
        val spacing = size * castedData.buttonSpacing
        val centerX = w / 2
        val centerY = h / 2

        when (castedData.style) {
            ControlData.DPad.Style.CROSS -> {
                // 十字形布局
                val halfButton = buttonSize / 2
                
                // 上
                upRect.set(
                    centerX - halfButton,
                    centerY - buttonSize - spacing / 2,
                    centerX + halfButton,
                    centerY - spacing / 2
                )
                
                // 下
                downRect.set(
                    centerX - halfButton,
                    centerY + spacing / 2,
                    centerX + halfButton,
                    centerY + buttonSize + spacing / 2
                )
                
                // 左
                leftRect.set(
                    centerX - buttonSize - spacing / 2,
                    centerY - halfButton,
                    centerX - spacing / 2,
                    centerY + halfButton
                )
                
                // 右
                rightRect.set(
                    centerX + spacing / 2,
                    centerY - halfButton,
                    centerX + buttonSize + spacing / 2,
                    centerY + halfButton
                )
                
                // 中心
                centerRect.set(
                    centerX - halfButton,
                    centerY - halfButton,
                    centerX + halfButton,
                    centerY + halfButton
                )
            }
            
            ControlData.DPad.Style.SQUARE -> {
                // 方形布局（紧凑十字形，无间距）
                val halfButton = buttonSize / 2
                
                // 上 - 顶部中间
                upRect.set(
                    centerX - halfButton,
                    centerY - buttonSize - halfButton,
                    centerX + halfButton,
                    centerY - halfButton
                )
                
                // 下 - 底部中间
                downRect.set(
                    centerX - halfButton,
                    centerY + halfButton,
                    centerX + halfButton,
                    centerY + buttonSize + halfButton
                )
                
                // 左 - 左侧中间
                leftRect.set(
                    centerX - buttonSize - halfButton,
                    centerY - halfButton,
                    centerX - halfButton,
                    centerY + halfButton
                )
                
                // 右 - 右侧中间
                rightRect.set(
                    centerX + halfButton,
                    centerY - halfButton,
                    centerX + buttonSize + halfButton,
                    centerY + halfButton
                )
                
                // 中心方块
                centerRect.set(
                    centerX - halfButton,
                    centerY - halfButton,
                    centerX + halfButton,
                    centerY + halfButton
                )
            }
            
            ControlData.DPad.Style.ROUND -> {
                // 圆形布局（整个控件区域）
                val radius = size / 2
                upRect.set(0f, 0f, w, h)
                downRect.set(0f, 0f, w, h)
                leftRect.set(0f, 0f, w, h)
                rightRect.set(0f, 0f, w, h)
                centerRect.set(
                    centerX - radius * castedData.deadZone,
                    centerY - radius * castedData.deadZone,
                    centerX + radius * castedData.deadZone,
                    centerY + radius * castedData.deadZone
                )
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        when (castedData.style) {
            ControlData.DPad.Style.CROSS -> drawCrossStyle(canvas)
            ControlData.DPad.Style.SQUARE -> drawSquareStyle(canvas)
            ControlData.DPad.Style.ROUND -> drawRoundStyle(canvas)
        }
    }

    private fun drawCrossStyle(canvas: Canvas) {
        val cornerRadius = min(upRect.width(), upRect.height()) * 0.2f
        
        // 绘制中心
        canvas.drawRoundRect(centerRect, cornerRadius, cornerRadius, mButtonPaint)
        canvas.drawRoundRect(centerRect, cornerRadius, cornerRadius, mStrokePaint)
        
        // 绘制上
        val upPaint = if (activeDirections and DIR_UP != 0) mActivePaint else mButtonPaint
        canvas.drawRoundRect(upRect, cornerRadius, cornerRadius, upPaint)
        canvas.drawRoundRect(upRect, cornerRadius, cornerRadius, mStrokePaint)
        if (castedData.showLabels) {
            drawTextCentered(canvas, "↑", upRect)
        }
        
        // 绘制下
        val downPaint = if (activeDirections and DIR_DOWN != 0) mActivePaint else mButtonPaint
        canvas.drawRoundRect(downRect, cornerRadius, cornerRadius, downPaint)
        canvas.drawRoundRect(downRect, cornerRadius, cornerRadius, mStrokePaint)
        if (castedData.showLabels) {
            drawTextCentered(canvas, "↓", downRect)
        }
        
        // 绘制左
        val leftPaint = if (activeDirections and DIR_LEFT != 0) mActivePaint else mButtonPaint
        canvas.drawRoundRect(leftRect, cornerRadius, cornerRadius, leftPaint)
        canvas.drawRoundRect(leftRect, cornerRadius, cornerRadius, mStrokePaint)
        if (castedData.showLabels) {
            drawTextCentered(canvas, "←", leftRect)
        }
        
        // 绘制右
        val rightPaint = if (activeDirections and DIR_RIGHT != 0) mActivePaint else mButtonPaint
        canvas.drawRoundRect(rightRect, cornerRadius, cornerRadius, rightPaint)
        canvas.drawRoundRect(rightRect, cornerRadius, cornerRadius, mStrokePaint)
        if (castedData.showLabels) {
            drawTextCentered(canvas, "→", rightRect)
        }
    }

    private fun drawSquareStyle(canvas: Canvas) {
        // 方形样式：无圆角，紧凑十字形
        val cornerRadius = 0f
        
        // 绘制中心
        canvas.drawRect(centerRect, mButtonPaint)
        canvas.drawRect(centerRect, mStrokePaint)
        
        // 上
        val upPaint = if (activeDirections and DIR_UP != 0) mActivePaint else mButtonPaint
        canvas.drawRect(upRect, upPaint)
        canvas.drawRect(upRect, mStrokePaint)
        if (castedData.showLabels) {
            drawTextCentered(canvas, "↑", upRect)
        }
        
        // 下
        val downPaint = if (activeDirections and DIR_DOWN != 0) mActivePaint else mButtonPaint
        canvas.drawRect(downRect, downPaint)
        canvas.drawRect(downRect, mStrokePaint)
        if (castedData.showLabels) {
            drawTextCentered(canvas, "↓", downRect)
        }
        
        // 左
        val leftPaint = if (activeDirections and DIR_LEFT != 0) mActivePaint else mButtonPaint
        canvas.drawRect(leftRect, leftPaint)
        canvas.drawRect(leftRect, mStrokePaint)
        if (castedData.showLabels) {
            drawTextCentered(canvas, "←", leftRect)
        }
        
        // 右
        val rightPaint = if (activeDirections and DIR_RIGHT != 0) mActivePaint else mButtonPaint
        canvas.drawRect(rightRect, rightPaint)
        canvas.drawRect(rightRect, mStrokePaint)
        if (castedData.showLabels) {
            drawTextCentered(canvas, "→", rightRect)
        }
    }

    private fun drawRoundStyle(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f
        val deadZoneRadius = radius * castedData.deadZone

        // 绘制外圈背景
        canvas.drawCircle(centerX, centerY, radius, mButtonPaint)
        canvas.drawCircle(centerX, centerY, radius, mStrokePaint)

        // 绘制四个扇区（用路径绘制激活状态）
        val sectorPath = Path()
        
        // 上扇区
        if (activeDirections and DIR_UP != 0) {
            sectorPath.reset()
            sectorPath.moveTo(centerX, centerY - deadZoneRadius)
            sectorPath.lineTo(centerX - radius * 0.7f, centerY - radius * 0.7f)
            sectorPath.arcTo(RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius), 225f, 90f)
            sectorPath.lineTo(centerX, centerY - deadZoneRadius)
            sectorPath.close()
            canvas.drawPath(sectorPath, mActivePaint)
        }
        
        // 下扇区
        if (activeDirections and DIR_DOWN != 0) {
            sectorPath.reset()
            sectorPath.moveTo(centerX, centerY + deadZoneRadius)
            sectorPath.lineTo(centerX + radius * 0.7f, centerY + radius * 0.7f)
            sectorPath.arcTo(RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius), 45f, 90f)
            sectorPath.lineTo(centerX, centerY + deadZoneRadius)
            sectorPath.close()
            canvas.drawPath(sectorPath, mActivePaint)
        }
        
        // 左扇区
        if (activeDirections and DIR_LEFT != 0) {
            sectorPath.reset()
            sectorPath.moveTo(centerX - deadZoneRadius, centerY)
            sectorPath.lineTo(centerX - radius * 0.7f, centerY + radius * 0.7f)
            sectorPath.arcTo(RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius), 135f, 90f)
            sectorPath.lineTo(centerX - deadZoneRadius, centerY)
            sectorPath.close()
            canvas.drawPath(sectorPath, mActivePaint)
        }
        
        // 右扇区
        if (activeDirections and DIR_RIGHT != 0) {
            sectorPath.reset()
            sectorPath.moveTo(centerX + deadZoneRadius, centerY)
            sectorPath.lineTo(centerX + radius * 0.7f, centerY - radius * 0.7f)
            sectorPath.arcTo(RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius), -45f, 90f)
            sectorPath.lineTo(centerX + deadZoneRadius, centerY)
            sectorPath.close()
            canvas.drawPath(sectorPath, mActivePaint)
        }

        // 绘制十字分隔线
        val dividerPaint = Paint(mStrokePaint).apply {
            alpha = (castedData.borderOpacity * 128).toInt()
        }
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, dividerPaint)
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, dividerPaint)

        // 绘制中心死区
        canvas.drawCircle(centerX, centerY, deadZoneRadius, mButtonPaint)
        canvas.drawCircle(centerX, centerY, deadZoneRadius, mStrokePaint)

        // 绘制方向标签
        if (castedData.showLabels) {
            val labelOffset = radius * 0.6f
            drawTextAt(canvas, "↑", centerX, centerY - labelOffset)
            drawTextAt(canvas, "↓", centerX, centerY + labelOffset)
            drawTextAt(canvas, "←", centerX - labelOffset, centerY)
            drawTextAt(canvas, "→", centerX + labelOffset, centerY)
        }
    }

    private fun drawTextCentered(canvas: Canvas, text: String, rect: RectF) {
        val x = rect.centerX()
        val y = rect.centerY() - (mTextPaint.descent() + mTextPaint.ascent()) / 2
        canvas.drawText(text, x, y, mTextPaint)
    }

    private fun drawTextAt(canvas: Canvas, text: String, x: Float, y: Float) {
        val adjustedY = y - (mTextPaint.descent() + mTextPaint.ascent()) / 2
        canvas.drawText(text, x, adjustedY, mTextPaint)
    }

    // ControlView 接口实现
    override fun isTouchInBounds(x: Float, y: Float): Boolean {
        return x >= 0 && x <= width && y >= 0 && y <= height
    }

    override fun tryAcquireTouch(pointerId: Int, x: Float, y: Float): Boolean {
        if (mTouchPointerId != -1) return false
        if (!isTouchInBounds(x, y)) return false
        
        mTouchPointerId = pointerId
        updateActiveDirections(x, y)
        sendKeyEvents()
        vibrationManager?.vibrateOneShot(50, 30)
        return true
    }

    override fun handleTouchMove(pointerId: Int, x: Float, y: Float) {
        if (pointerId != mTouchPointerId) return
        updateActiveDirections(x, y)
        sendKeyEvents()
    }

    override fun releaseTouch(pointerId: Int) {
        if (pointerId != mTouchPointerId) return
        mTouchPointerId = -1
        releaseAllKeys()
        activeDirections = DIR_NONE
        invalidate()
    }

    override fun cancelAllTouches() {
        if (mTouchPointerId != -1) {
            releaseAllKeys()
            mTouchPointerId = -1
            activeDirections = DIR_NONE
            invalidate()
        }
    }

    private fun updateActiveDirections(x: Float, y: Float) {
        previousDirections = activeDirections
        
        when (castedData.style) {
            ControlData.DPad.Style.CROSS, ControlData.DPad.Style.SQUARE -> {
                activeDirections = DIR_NONE
                
                if (upRect.contains(x, y)) activeDirections = activeDirections or DIR_UP
                if (downRect.contains(x, y)) activeDirections = activeDirections or DIR_DOWN
                if (leftRect.contains(x, y)) activeDirections = activeDirections or DIR_LEFT
                if (rightRect.contains(x, y)) activeDirections = activeDirections or DIR_RIGHT
                
                // 如果允许斜向，检查是否在角落区域
                if (castedData.allowDiagonal && castedData.style == ControlData.DPad.Style.CROSS) {
                    val centerX = width / 2f
                    val centerY = height / 2f
                    val dx = x - centerX
                    val dy = y - centerY
                    val threshold = min(width, height) * 0.15f
                    
                    // 左上角
                    if (dx < -threshold && dy < -threshold) {
                        activeDirections = DIR_UP or DIR_LEFT
                    }
                    // 右上角
                    else if (dx > threshold && dy < -threshold) {
                        activeDirections = DIR_UP or DIR_RIGHT
                    }
                    // 左下角
                    else if (dx < -threshold && dy > threshold) {
                        activeDirections = DIR_DOWN or DIR_LEFT
                    }
                    // 右下角
                    else if (dx > threshold && dy > threshold) {
                        activeDirections = DIR_DOWN or DIR_RIGHT
                    }
                }
            }
            
            ControlData.DPad.Style.ROUND -> {
                val centerX = width / 2f
                val centerY = height / 2f
                val dx = x - centerX
                val dy = y - centerY
                val distance = sqrt(dx * dx + dy * dy)
                val radius = min(width, height) / 2f
                val deadZoneRadius = radius * castedData.deadZone
                
                activeDirections = DIR_NONE
                
                // 在死区内不触发任何方向
                if (distance < deadZoneRadius) {
                    invalidate()
                    return
                }
                
                // 计算角度 (0° = 右, 逆时针)
                val angle = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())).toFloat()
                
                if (castedData.allowDiagonal) {
                    // 8向模式
                    when {
                        angle >= -22.5f && angle < 22.5f -> activeDirections = DIR_RIGHT
                        angle >= 22.5f && angle < 67.5f -> activeDirections = DIR_UP or DIR_RIGHT
                        angle >= 67.5f && angle < 112.5f -> activeDirections = DIR_UP
                        angle >= 112.5f && angle < 157.5f -> activeDirections = DIR_UP or DIR_LEFT
                        angle >= 157.5f || angle < -157.5f -> activeDirections = DIR_LEFT
                        angle >= -157.5f && angle < -112.5f -> activeDirections = DIR_DOWN or DIR_LEFT
                        angle >= -112.5f && angle < -67.5f -> activeDirections = DIR_DOWN
                        angle >= -67.5f && angle < -22.5f -> activeDirections = DIR_DOWN or DIR_RIGHT
                    }
                } else {
                    // 4向模式
                    when {
                        angle >= -45f && angle < 45f -> activeDirections = DIR_RIGHT
                        angle >= 45f && angle < 135f -> activeDirections = DIR_UP
                        angle >= 135f || angle < -135f -> activeDirections = DIR_LEFT
                        else -> activeDirections = DIR_DOWN
                    }
                }
            }
        }
        
        invalidate()
    }

    private fun sendKeyEvents() {
        val pressed = activeDirections
        val released = previousDirections and activeDirections.inv()
        
        // 释放不再激活的方向
        if (released and DIR_UP != 0) sendKeyUp(castedData.upKeycode)
        if (released and DIR_DOWN != 0) sendKeyUp(castedData.downKeycode)
        if (released and DIR_LEFT != 0) sendKeyUp(castedData.leftKeycode)
        if (released and DIR_RIGHT != 0) sendKeyUp(castedData.rightKeycode)
        
        // 按下新激活的方向
        val newPressed = pressed and previousDirections.inv()
        if (newPressed and DIR_UP != 0) sendKeyDown(castedData.upKeycode)
        if (newPressed and DIR_DOWN != 0) sendKeyDown(castedData.downKeycode)
        if (newPressed and DIR_LEFT != 0) sendKeyDown(castedData.leftKeycode)
        if (newPressed and DIR_RIGHT != 0) sendKeyDown(castedData.rightKeycode)
        
        // 震动反馈（方向变化时）
        if (newPressed != 0) {
            vibrationManager?.vibrateOneShot(30, 20)
        }
    }

    private fun sendKeyDown(keycode: ControlData.KeyCode) {
        when (castedData.mode) {
            ControlData.Button.Mode.KEYBOARD -> {
                mInputBridge.sendKey(keycode, true)
            }
            ControlData.Button.Mode.GAMEPAD -> {
                mInputBridge.sendXboxButton(keycode, true)
            }
        }
    }

    private fun sendKeyUp(keycode: ControlData.KeyCode) {
        when (castedData.mode) {
            ControlData.Button.Mode.KEYBOARD -> {
                mInputBridge.sendKey(keycode, false)
            }
            ControlData.Button.Mode.GAMEPAD -> {
                mInputBridge.sendXboxButton(keycode, false)
            }
        }
    }

    private fun releaseAllKeys() {
        if (previousDirections and DIR_UP != 0) sendKeyUp(castedData.upKeycode)
        if (previousDirections and DIR_DOWN != 0) sendKeyUp(castedData.downKeycode)
        if (previousDirections and DIR_LEFT != 0) sendKeyUp(castedData.leftKeycode)
        if (previousDirections and DIR_RIGHT != 0) sendKeyUp(castedData.rightKeycode)
        previousDirections = DIR_NONE
    }
}
