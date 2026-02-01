package com.app.ralaunch.controls.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.app.ralaunch.controls.bridges.ControlInputBridge
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.textures.TextureLoader
import com.app.ralaunch.manager.VibrationManager
import org.koin.java.KoinJavaComponent
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 轮盘菜单控件
 * 
 * 点击后展开为圆形菜单，拖动选择方向触发对应按键
 */
class VirtualRadialMenu(
    context: Context?,
    data: ControlData,
    private val mInputBridge: ControlInputBridge
) : View(context), ControlView {

    companion object {
        private const val TAG = "VirtualRadialMenu"
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

    private val castedData: ControlData.RadialMenu
        get() = controlData as ControlData.RadialMenu

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
    private lateinit var mSectorPaint: Paint
    private lateinit var mSelectedPaint: Paint
    private lateinit var mDividerPaint: Paint
    private lateinit var mTextPaint: TextPaint
    private lateinit var mStrokePaint: Paint
    private val mRectF = RectF()
    private val mSectorPath = Path()

    // 状态
    private var mIsExpanded = false
    private var mExpandProgress = 0f // 0.0 = 收起, 1.0 = 展开
    private var mSelectedSector = -1 // 当前选中的扇区 (-1 = 无)
    private var mActivePointerId = -1
    private var mTouchStartX = 0f
    private var mTouchStartY = 0f
    private var mCurrentTouchX = 0f
    private var mCurrentTouchY = 0f

    // 动画
    private var mExpandAnimator: ValueAnimator? = null

    init {
        initPaints()
    }

    private fun initPaints() {
        val data = castedData

        // 背景画笔
        mBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(
                (data.opacity * 255).toInt(),
                Color.red(data.bgColor),
                Color.green(data.bgColor),
                Color.blue(data.bgColor)
            )
        }

        // 扇区画笔
        mSectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(
                (data.opacity * 0.8f * 255).toInt(),
                Color.red(data.bgColor),
                Color.green(data.bgColor),
                Color.blue(data.bgColor)
            )
        }

        // 选中扇区画笔
        mSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = data.selectedColor
        }

        // 分隔线画笔
        mDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = data.dividerColor
            strokeWidth = 2f
        }

        // 边框画笔
        mStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(
                (data.borderOpacity * 255).toInt(),
                Color.red(data.strokeColor),
                Color.green(data.strokeColor),
                Color.blue(data.strokeColor)
            )
            strokeWidth = dpToPx(2f)
        }

        // 文字画笔
        mTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = data.textColor
            alpha = (data.textOpacity * 255).toInt()
            textSize = dpToPx(14f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val baseRadius = min(width, height) / 2f

        if (mExpandProgress > 0f) {
            // 绘制展开状态
            drawExpandedState(canvas, centerX, centerY, baseRadius)
        } else {
            // 绘制收起状态
            drawCollapsedState(canvas, centerX, centerY, baseRadius)
        }
    }

    private fun drawCollapsedState(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val data = castedData
        
        // 绘制背景圆
        canvas.drawCircle(centerX, centerY, radius, mBackgroundPaint)
        
        // 绘制边框
        if (data.borderOpacity > 0) {
            canvas.drawCircle(centerX, centerY, radius - dpToPx(1f), mStrokePaint)
        }

        // 绘制中心文本或图标
        val text = data.name.ifEmpty { "◎" }
        mTextPaint.textSize = dpToPx(16f)
        val textY = centerY - (mTextPaint.descent() + mTextPaint.ascent()) / 2
        canvas.drawText(text, centerX, textY, mTextPaint)
    }

    private fun drawExpandedState(canvas: Canvas, centerX: Float, centerY: Float, baseRadius: Float) {
        val data = castedData
        val expandedRadius = baseRadius * data.expandedScale * mExpandProgress
        val deadZoneRadius = expandedRadius * data.deadZoneRatio
        val sectorCount = data.sectorCount
        val sectorAngle = 360f / sectorCount

        // 绘制整体背景
        mBackgroundPaint.alpha = (data.opacity * 0.9f * 255 * mExpandProgress).toInt()
        canvas.drawCircle(centerX, centerY, expandedRadius, mBackgroundPaint)

        // 绘制各扇区
        for (i in 0 until sectorCount) {
            val startAngle = -90f + i * sectorAngle - sectorAngle / 2
            
            // 选中高亮
            if (i == mSelectedSector) {
                mSectorPath.reset()
                mSectorPath.moveTo(centerX, centerY)
                mRectF.set(
                    centerX - expandedRadius,
                    centerY - expandedRadius,
                    centerX + expandedRadius,
                    centerY + expandedRadius
                )
                mSectorPath.arcTo(mRectF, startAngle, sectorAngle)
                mSectorPath.close()
                canvas.drawPath(mSectorPath, mSelectedPaint)
            }

            // 扇区分隔线
            if (data.showDividers) {
                val angleRad = Math.toRadians((startAngle).toDouble())
                val lineStartX = centerX + (deadZoneRadius * cos(angleRad)).toFloat()
                val lineStartY = centerY + (deadZoneRadius * sin(angleRad)).toFloat()
                val lineEndX = centerX + (expandedRadius * cos(angleRad)).toFloat()
                val lineEndY = centerY + (expandedRadius * sin(angleRad)).toFloat()
                canvas.drawLine(lineStartX, lineStartY, lineEndX, lineEndY, mDividerPaint)
            }

            // 扇区文本/图标
            if (i < data.sectors.size) {
                val sector = data.sectors[i]
                val midAngle = startAngle + sectorAngle / 2
                val midAngleRad = Math.toRadians(midAngle.toDouble())
                val labelRadius = (deadZoneRadius + expandedRadius) / 2
                val labelX = centerX + (labelRadius * cos(midAngleRad)).toFloat()
                val labelY = centerY + (labelRadius * sin(midAngleRad)).toFloat()

                val label = sector.label.ifEmpty { sector.keycode.name.removePrefix("KEYBOARD_") }
                
                // 调整文字大小和透明度
                mTextPaint.textSize = dpToPx(12f) * mExpandProgress
                mTextPaint.alpha = (data.textOpacity * 255 * mExpandProgress).toInt()
                
                val textY = labelY - (mTextPaint.descent() + mTextPaint.ascent()) / 2
                canvas.drawText(label, labelX, textY, mTextPaint)
            }
        }

        // 绘制中心死区
        mBackgroundPaint.alpha = (data.opacity * 255).toInt()
        canvas.drawCircle(centerX, centerY, deadZoneRadius, mBackgroundPaint)

        // 绘制边框
        if (data.borderOpacity > 0) {
            canvas.drawCircle(centerX, centerY, expandedRadius - dpToPx(1f), mStrokePaint)
            canvas.drawCircle(centerX, centerY, deadZoneRadius - dpToPx(1f), mStrokePaint)
        }

        // 绘制当前触摸位置指示
        if (mActivePointerId >= 0) {
            val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.WHITE
                alpha = 150
            }
            canvas.drawCircle(mCurrentTouchX, mCurrentTouchY, 15f, indicatorPaint)
        }
    }

    // 展开/收起动画
    private fun animateExpand(expand: Boolean) {
        mExpandAnimator?.cancel()
        
        val targetProgress = if (expand) 1f else 0f
        mExpandAnimator = ValueAnimator.ofFloat(mExpandProgress, targetProgress).apply {
            duration = castedData.expandDuration.toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                mExpandProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
        
        mIsExpanded = expand
    }

    // 计算当前选中的扇区
    private fun calculateSelectedSector(touchX: Float, touchY: Float): Int {
        val data = castedData
        val centerX = width / 2f
        val centerY = height / 2f
        val baseRadius = min(width, height) / 2f
        val expandedRadius = baseRadius * data.expandedScale
        val deadZoneRadius = expandedRadius * data.deadZoneRatio

        val dx = touchX - centerX
        val dy = touchY - centerY
        val distance = sqrt(dx * dx + dy * dy)

        // 在死区内不选中任何扇区
        if (distance < deadZoneRadius) {
            return -1
        }

        // 计算角度 (0° = 上方, 顺时针增加)
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
        if (angle < 0) angle += 360f

        // 计算扇区索引
        val sectorAngle = 360f / data.sectorCount
        return ((angle + sectorAngle / 2) % 360 / sectorAngle).toInt()
    }

    // 触发按键
    private fun triggerSectorKey(sectorIndex: Int, pressed: Boolean) {
        if (sectorIndex < 0 || sectorIndex >= castedData.sectors.size) return
        
        val sector = castedData.sectors[sectorIndex]
        val keycode = sector.keycode
        
        if (keycode != ControlData.KeyCode.UNKNOWN) {
            if (pressed) {
                vibrationManager?.vibrateOneShot(30, 50)
            }
            
            when (keycode.type) {
                ControlData.KeyType.KEYBOARD -> {
                    mInputBridge.sendKey(keycode, pressed)
                }
                ControlData.KeyType.MOUSE -> {
                    mInputBridge.sendMouseButton(keycode, pressed, width / 2f, height / 2f)
                }
                ControlData.KeyType.GAMEPAD -> {
                    mInputBridge.sendXboxButton(keycode, pressed)
                }
                else -> {}
            }
        }
    }

    // ControlView 接口实现
    override fun isTouchInBounds(x: Float, y: Float): Boolean {
        val localX = x - left
        val localY = y - top
        val centerX = width / 2f
        val centerY = height / 2f
        
        val radius = if (mIsExpanded) {
            min(width, height) / 2f * castedData.expandedScale
        } else {
            min(width, height) / 2f
        }
        
        val dx = localX - centerX
        val dy = localY - centerY
        return sqrt(dx * dx + dy * dy) <= radius
    }

    override fun tryAcquireTouch(pointerId: Int, x: Float, y: Float): Boolean {
        if (mActivePointerId >= 0) return false

        val centerX = width / 2f
        val centerY = height / 2f
        val baseRadius = min(width, height) / 2f
        
        val dx = x - centerX
        val dy = y - centerY
        val distance = sqrt(dx * dx + dy * dy)

        // 检查是否在收起状态的圆内
        if (!mIsExpanded && distance <= baseRadius) {
            mActivePointerId = pointerId
            mTouchStartX = x
            mTouchStartY = y
            mCurrentTouchX = x
            mCurrentTouchY = y
            
            // 展开轮盘
            animateExpand(true)
            return true
        }
        
        // 如果已展开，检查是否在展开范围内
        if (mIsExpanded && distance <= baseRadius * castedData.expandedScale) {
            mActivePointerId = pointerId
            mCurrentTouchX = x
            mCurrentTouchY = y
            mSelectedSector = calculateSelectedSector(x, y)
            invalidate()
            return true
        }

        return false
    }

    override fun handleTouchMove(pointerId: Int, x: Float, y: Float) {
        if (pointerId != mActivePointerId) return
        
        mCurrentTouchX = x
        mCurrentTouchY = y
        
        val newSector = calculateSelectedSector(x, y)
        if (newSector != mSelectedSector) {
            // 扇区变化时震动提示
            if (newSector >= 0) {
                vibrationManager?.vibrateOneShot(15, 30)
            }
            mSelectedSector = newSector
        }
        
        invalidate()
    }

    override fun releaseTouch(pointerId: Int) {
        if (pointerId != mActivePointerId) return
        
        // 触发选中扇区的按键
        if (mSelectedSector >= 0) {
            triggerSectorKey(mSelectedSector, true)
            // 短暂延迟后释放按键
            postDelayed({
                triggerSectorKey(mSelectedSector, false)
            }, 50)
        }
        
        // 收起轮盘
        animateExpand(false)
        
        mActivePointerId = -1
        mSelectedSector = -1
        invalidate()
    }

    override fun cancelAllTouches() {
        if (mActivePointerId >= 0) {
            animateExpand(false)
            mActivePointerId = -1
            mSelectedSector = -1
            invalidate()
        }
    }
}
