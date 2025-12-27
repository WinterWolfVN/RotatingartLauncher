package com.app.ralaunch.controls.views

import android.R
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextPaint
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.activity.GameActivity
import com.app.ralaunch.controls.configs.ControlData
import com.app.ralaunch.controls.bridges.ControlInputBridge
import com.app.ralaunch.controls.views.ControlView
import com.app.ralaunch.controls.TouchPointerTracker
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min

/**
 * 虚拟按钮View
 * 支持普通按钮和切换按钮（Toggle）
 */
class VirtualButton(
    context: Context?,
    data: ControlData,
    private val mInputBridge: ControlInputBridge
) : View(context), ControlView {

    companion object {
        private const val TAG = "VirtualButton"

        private fun triggerVibration(isPress: Boolean) {
            if (isPress) {
                RaLaunchApplication.getVibrationManager().vibrateOneShot(50, 30)
            } else {
                // 释放时不振动
//            RaLaunchApplication.getVibrationManager().vibrateOneShot(50, 30);
            }
        }
    }

    override var controlData: ControlData = data
        set(value) {
            field = value
            initPaints()
            invalidate()
        }
    
    private val castedData: ControlData.Button
        get() = controlData as ControlData.Button

    // 绘制相关
    private var mBackgroundPaint: Paint? = null    // initialized in initPaints
    private var mStrokePaint: Paint? = null        // initialized in initPaints
    private var mTextPaint: TextPaint? = null      // initialized in initPaints
    private val mRectF: RectF = RectF()

    // 按钮状态
    private var mIsPressed = false
    private var mIsToggled = false
    private var mActivePointerId = -1 // 跟踪的触摸点 ID

    var isPressedState: Boolean
        /**
         * 获取按下状态
         */
        get() = mIsPressed
        /**
         * 设置按下状态（用于编辑模式的选择反馈）
         */
        set(pressed) {
            if (mIsPressed != pressed) {
                mIsPressed = pressed
                invalidate() // 刷新绘制
            }
        }

    init {
        initPaints()
    }

    private fun initPaints() {
        if (castedData.mode == ControlData.Button.Mode.GAMEPAD) {
            val normalColor = 0x7D7D7D7D // 半透明灰色
            val pressedColor = -0x828283 // 不透明灰色（按下）
            val textColor = 0x7DFFFFFF // 半透明白色（文字）
            val backgroundColor = 0x327D7D7D // 很淡的灰色（背景）

            mBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            mBackgroundPaint?.color = normalColor
            mBackgroundPaint?.style = Paint.Style.FILL
            mBackgroundPaint?.alpha = (castedData.opacity * 255).toInt()


            mStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            mStrokePaint?.color = 0x00000000 // 透明
            mStrokePaint?.style = Paint.Style.STROKE
            mStrokePaint?.strokeWidth = 0f

            mTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
            mTextPaint?.color = textColor
            mTextPaint?.typeface = Typeface.DEFAULT_BOLD // 粗体
            mTextPaint?.textAlign = Paint.Align.CENTER
            // 使用文本透明度（如果为0则默认不透明，确保文本可见）
            val textOpacity = if (castedData.textOpacity != 0f) castedData.textOpacity else 1.0f
            mTextPaint?.alpha = (textOpacity * 255).toInt()
        } else {
            // 键盘模式保持原有逻辑
            mBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            mBackgroundPaint?.color = castedData.bgColor
            mBackgroundPaint?.style = Paint.Style.FILL
            mBackgroundPaint?.alpha = (castedData.opacity * 255).toInt()

            mStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            mStrokePaint?.color = castedData.strokeColor
            mStrokePaint?.style = Paint.Style.STROKE
            mStrokePaint?.strokeWidth = dpToPx(castedData.strokeWidth)
            // 边框透明度完全独立，默认1.0（完全不透明）
            val borderOpacity = if (castedData.borderOpacity != 0f) castedData.borderOpacity else 1.0f
            mStrokePaint?.alpha = (borderOpacity * 255).toInt()

            mTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
            mTextPaint?.color = -0x1
            mTextPaint?.textSize = dpToPx(16f)
            mTextPaint?.textAlign = Paint.Align.CENTER
            // 文本透明度完全独立，默认1.0（完全不透明）
            val textOpacity = if (castedData.textOpacity != 0f) castedData.textOpacity else 1.0f
            mTextPaint?.alpha = (textOpacity * 255).toInt()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mRectF.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)


        // 根据形状类型绘制背景
        val shape = castedData.shape
        val centerXDraw = mRectF.centerX()
        val centerYDraw = mRectF.centerY()
        val radius = min(mRectF.width(), mRectF.height()) / 2f

        when (shape) {
            ControlData.Button.Shape.RECTANGLE -> {
                // 绘制矩形（圆角矩形）
                val cornerRadius = dpToPx(castedData.cornerRadius)
                canvas.drawRoundRect(mRectF, cornerRadius, cornerRadius, mBackgroundPaint!!)
                canvas.drawRoundRect(mRectF, cornerRadius, cornerRadius, mStrokePaint!!)
            }
            ControlData.Button.Shape.CIRCLE -> {
                when (castedData.mode) {
                    ControlData.Button.Mode.KEYBOARD -> {
                        // 普通圆形（键盘模式）
                        // 根据状态调整颜色
                        val alpha = mBackgroundPaint?.alpha
                        if (mIsPressed || mIsToggled) {
                            mBackgroundPaint?.alpha = min(255, (alpha!! * 1.5f).toInt())
                        } else {
                            mBackgroundPaint?.alpha = (castedData.opacity * 255).toInt()
                        }
                        canvas.drawCircle(centerXDraw, centerYDraw, radius, mBackgroundPaint!!)
                        canvas.drawCircle(centerXDraw, centerYDraw, radius, mStrokePaint!!)
                    }
                    ControlData.Button.Mode.GAMEPAD -> {
                        val margin = 0.15f // DEFAULT_MARGIN
                        val outerRadius = radius * (1.0f - margin) // 85% 半径（外圈）
                        val innerRadius = radius * (1.0f - 2 * margin) // 70% 半径（内圈）


                        // 绘制外圈（背景）
                        val backgroundPaint = Paint(mBackgroundPaint)
                        backgroundPaint.color = 0x327D7D7D // backgroundColor
                        backgroundPaint.alpha = (castedData.opacity * 255).toInt()
                        canvas.drawCircle(centerXDraw, centerYDraw, outerRadius, backgroundPaint)


                        // 绘制内圈（前景，按下时使用 pressedColor）
                        val foregroundPaint = Paint(mBackgroundPaint)
                        if (mIsPressed || mIsToggled) {
                            foregroundPaint.color = -0x828283 // pressedColor
                        } else {
                            foregroundPaint.color = 0x7D7D7D7D // normalColor
                        }
                        foregroundPaint.alpha = (castedData.opacity * 255).toInt()
                        canvas.drawCircle(centerXDraw, centerYDraw, innerRadius, foregroundPaint)
                    }
                }
            }
        }


        // 绘制文字（名称 + 按键）
        val displayText = castedData.name

        if (!displayText.isEmpty()) {
            // 保存 canvas 状态以便裁剪
            canvas.save()


            // 根据控件形状设置裁剪区域
            if (shape == ControlData.Button.Shape.CIRCLE) {
                // 圆形裁剪：使用圆形路径
                val clipPath = Path()
                clipPath.addCircle(centerXDraw, centerYDraw, radius, Path.Direction.CW)
                canvas.clipPath(clipPath)
            } else {
                // 矩形裁剪：使用矩形区域（留出一些边距）
                val padding = dpToPx(2f)
                canvas.clipRect(padding, padding, width - padding, height - padding)
            }


            // RadialGamePad 风格：自动计算文字大小以适应区域
            if (castedData.mode == ControlData.Button.Mode.GAMEPAD) {
                // 计算文字宽高比
                mTextPaint?.textSize = 20f // 临时设置用于测量
                val textBounds = Rect()
                mTextPaint?.getTextBounds(displayText, 0, displayText.length, textBounds)
                val textAspectRatio = textBounds.width() / max(textBounds.height(), 1).toFloat()


                // 自动计算文字大小：minOf(height / 2, width / textAspectRatio)
                val textSize = min(
                    height / 2f,
                    width / max(textAspectRatio, 1f)
                )
                mTextPaint?.textSize = textSize
            } else {
                // 键盘模式：检查文本宽度，如果超出则缩小字体
                mTextPaint?.textSize = dpToPx(16f)
                val textWidth = mTextPaint?.measureText(displayText)
                val availableWidth = width - dpToPx(4f) // 留出边距

                if (textWidth!! > availableWidth) {
                    // 文本超出，按比例缩小字体
                    val scale = availableWidth / textWidth!!
                    val newTextSize = mTextPaint?.textSize!! * scale
                    mTextPaint?.textSize = newTextSize
                }
            }


            // 只显示名称（居中）
            // 显示真实按键会挡视野
            val textY = height / 2f - ((mTextPaint!!.descent() + mTextPaint!!.ascent()) / 2)
            canvas.drawText(displayText, width / 2f, textY, mTextPaint!!)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerId = event.getPointerId(event.actionIndex)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // 如果已经在跟踪一个触摸点，忽略新的
                if (mActivePointerId != -1) {
                    return false
                }


                // 记录触摸点
                mActivePointerId = pointerId
                // 如果不穿透，标记这个触摸点被占用（不传递给游戏）
                if (!castedData.isPassThrough) {
                    TouchPointerTracker.consumePointer(pointerId)
                }

                handlePress()
                triggerVibration(true)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                // 检查是否是我们跟踪的触摸点
                if (pointerId == mActivePointerId) {
                    // 释放触摸点标记（如果之前标记了）
                    if (!castedData.isPassThrough) {
                        TouchPointerTracker.releasePointer(mActivePointerId)
                    }
                    mActivePointerId = -1

                    triggerVibration(false)
                    handleRelease()
                    return true
                }
                return false
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handlePress() {
        mIsPressed = true


        // 处理特殊功能按键
        if (castedData.keycode == ControlData.KeyCode.SPECIAL_KEYBOARD) {
            // 显示系统键盘
            showKeyboard()
            invalidate()
            return
        }

        if (castedData.isToggle) {
            // 切换按钮：切换状态
            mIsToggled = !mIsToggled
            sendInput(mIsToggled)
        } else {
            // 普通按钮：按下
            sendInput(true)
        }

        invalidate()
    }

    private fun handleRelease() {
        mIsPressed = false

        if (!castedData.isToggle) {
            // 普通按钮：释放
            sendInput(false)
        }

        // 切换按钮不在释放时发送事件
        invalidate()
    }

    /**
     * 显示Android软键盘并实时发送文本到游戏
     *
     * 工作原理：
     * 1. 先调用SDL.showTextInput()启用SDL文本输入模式
     * 2. 创建透明EditText激活系统IME
     * 3. 每输入一个字符立即通过SDL_TEXTINPUT发送
     * 4. Terraria的FnaIme会接收并转发文本到游戏
     */
    private fun showKeyboard() {
        try {
            if (context !is Activity) {
                Log.e(TAG, "Context is not an Activity")
                return
            }

            val activity = context as Activity

            activity.runOnUiThread(Runnable {
                try {
                    // 第一步：启用SDL文本输入模式（通过GameActivity）
                    GameActivity.enableSDLTextInputForIME()


                    // 等待100ms让SDL准备好（使用静态Handler避免内存泄漏）
                    val handler = Handler(Looper.getMainLooper())
                    handler.postDelayed(
                        KeyboardShowRunnable(
                            activity,
                            WeakReference<Context?>(context)
                        ), 100
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enable SDL text input", e)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show keyboard", e)
        }
    }

    private fun sendInput(isDown: Boolean) {

        when {
            castedData.keycode.type == ControlData.KeyType.KEYBOARD -> {
                // 键盘按键
                mInputBridge.sendKey(castedData.keycode, isDown)
            }
            castedData.keycode.code >= ControlData.KeyCode.XBOX_TRIGGER_RIGHT.code && castedData.keycode.code <= ControlData.KeyCode.XBOX_TRIGGER_LEFT.code -> {
                // Xbox控制器触发器 (范围: -220 到 -221)
                val triggerValue = if (isDown) 1.0f else -1.0f
                mInputBridge.sendXboxTrigger(castedData.keycode, triggerValue)
            }
            castedData.keycode.code >= ControlData.KeyCode.XBOX_BUTTON_DPAD_RIGHT.code && castedData.keycode.code <= ControlData.KeyCode.XBOX_BUTTON_A.code -> {
                // Xbox控制器按钮 (范围: -200 到 -214)
                mInputBridge.sendXboxButton(castedData.keycode, isDown)
            }
            castedData.keycode == ControlData.KeyCode.MOUSE_WHEEL_UP || castedData.keycode == ControlData.KeyCode.MOUSE_WHEEL_DOWN -> {
                // 鼠标滚轮 (MOUSE_WHEEL_UP=-4, MOUSE_WHEEL_DOWN=-5)
                // 只在按下时发送滚轮事件，释放时不发送
                if (isDown) {
                    val scrollY = if (castedData.keycode == ControlData.KeyCode.MOUSE_WHEEL_UP) 1.0f else -1.0f
                    mInputBridge.sendMouseWheel(scrollY)
                }
            }
            castedData.keycode.code >= ControlData.KeyCode.MOUSE_MIDDLE.code && castedData.keycode.code <= ControlData.KeyCode.MOUSE_LEFT.code -> {
                // 鼠标按键 (范围: -1 到 -3)
                // 计算按钮中心点的屏幕坐标
                val location = IntArray(2)
                getLocationOnScreen(location)
                val centerX = location[0] + width / 2.0f
                val centerY = location[1] + height / 2.0f

                mInputBridge.sendMouseButton(castedData.keycode, isDown, centerX, centerY)
            }
            // SPECIAL_KEYBOARD (-100) 不在这里处理，在handlePress中特殊处理
        }
    }

    /**
     * 重置切换按钮状态
     */
    fun resetToggle() {
        if (mIsToggled) {
            mIsToggled = false
            sendInput(false)
            invalidate()
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    /**
     * 静态内部类，避免隐式持有外部类引用，防止内存泄漏
     */
    private class KeyboardShowRunnable(
        activity: Activity?,
        private val contextRef: WeakReference<Context?>
    ) : Runnable {
        private val activityRef: WeakReference<Activity?>

        init {
            this.activityRef = WeakReference<Activity?>(activity)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun run() {
            val activity = activityRef.get()
            val context = contextRef.get()
            if (activity == null || activity.isFinishing || context == null) {
                Log.w(TAG, "Activity已销毁，取消键盘显示")
                return
            }

            try {
                // 标志位防止重复清理（需要在创建EditText之前声明，因为会在监听器中使用）
                val isCleanedUp = booleanArrayOf(false)


                // 第二步：创建透明EditText激活IME
                val dummyInput = EditText(activity)
                dummyInput.alpha = 0f
                dummyInput.width = 1
                dummyInput.height = 1


                // 设置输入法选项，防止全屏模式，让键盘以浮动模式显示
                dummyInput.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or
                        EditorInfo.IME_FLAG_NO_EXTRACT_UI


                // 关键修复：确保EditText在触摸模式下也能保持焦点
                // 这样即使其他视图消费了触摸事件，键盘也不会关闭
                dummyInput.setFocusable(true)
                dummyInput.isFocusableInTouchMode = true
                dummyInput.isClickable = true
                dummyInput.isLongClickable = false


                // 防止触摸事件导致焦点丢失
                // 当触摸事件发生时，重新请求焦点以保持键盘显示
                dummyInput.setOnTouchListener { v: View?, event: MotionEvent? ->
                    // 如果失去焦点，立即重新获取焦点
                    if (!v!!.hasFocus()) {
                        v.post {
                            if (v != null && v.parent != null) {
                                v.requestFocus()
                            }
                        }
                    }
                    false // 不消费事件，让系统正常处理
                }


                // 监听焦点变化，如果意外失去焦点则重新获取
                dummyInput.onFocusChangeListener = OnFocusChangeListener { v: View?, hasFocus: Boolean ->
                    if (!hasFocus && !isCleanedUp[0]) {
                        Log.d(TAG, "EditText失去焦点，准备重新获取焦点以保持键盘显示")
                        // 延迟重新获取焦点，避免与其他事件冲突
                        v!!.postDelayed(Runnable {
                            if (v != null && v.parent != null && !isCleanedUp[0]) {
                                Log.d(TAG, "重新获取EditText焦点")
                                v.requestFocus()
                                // 确保键盘仍然显示
                                val imm =
                                    context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                                imm?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                            }
                        }, 50)
                    }
                }


                val rootView = activity.findViewById<View?>(R.id.content) as ViewGroup?
                if (rootView == null) {
                    Log.e(TAG, "Root view not found")
                    return
                }


                // 创建LayoutParams，将EditText放置在屏幕中央
                val layoutParams =
                    FrameLayout.LayoutParams(1, 1)
                layoutParams.gravity = Gravity.CENTER

                rootView.addView(dummyInput, layoutParams)


                // 创建Handler用于30秒超时
                val timeoutHandler = Handler(Looper.getMainLooper())
                val timeoutRunnable = arrayOfNulls<Runnable>(1) // 数组是为了能在lambda中修改


                // 创建清理函数
                val focusKeeper = arrayOfNulls<Runnable>(1) // 提前声明，供清理函数使用
                val cleanup = Runnable {
                    if (isCleanedUp[0]) {
                        return@Runnable  // 已经清理过了，不要重复执行
                    }
                    isCleanedUp[0] = true
                    try {
                        // 移除30秒超时
                        if (timeoutRunnable[0] != null) {
                            timeoutHandler.removeCallbacks(timeoutRunnable[0]!!)
                        }


                        // 停止焦点保持检查
                        if (focusKeeper[0] != null) {
                            timeoutHandler.removeCallbacks(focusKeeper[0]!!)
                        }


                        // 先禁用SDL文本输入（最重要！）
                        GameActivity.disableSDLTextInput()


                        // 隐藏键盘
                        val imm =
                            context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                        imm?.hideSoftInputFromWindow(dummyInput.windowToken, 0)


                        // 移除EditText
                        rootView.removeView(dummyInput)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to cleanup input", e)
                    }
                }


                // 监听键盘隐藏事件
                val layoutListener: ViewTreeObserver.OnGlobalLayoutListener =
                    object : ViewTreeObserver.OnGlobalLayoutListener {
                        private var wasKeyboardVisible = false

                        override fun onGlobalLayout() {
                            val r = Rect()
                            rootView.getWindowVisibleDisplayFrame(r)
                            val screenHeight = rootView.rootView.height
                            val keypadHeight = screenHeight - r.bottom

                            val isKeyboardVisible = keypadHeight > screenHeight * 0.15

                            if (wasKeyboardVisible && !isKeyboardVisible) {
                                // 键盘从显示变为隐藏
                                rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                cleanup.run()
                            }

                            wasKeyboardVisible = isKeyboardVisible
                        }
                    }

                rootView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)


                // 显示键盘
                dummyInput.requestFocus()
                val imm =
                    context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                imm?.showSoftInput(dummyInput, InputMethodManager.SHOW_FORCED)


                // 定期检查并保持焦点（针对不同设备的键盘行为差异）
                // 有些设备的系统键盘在点击其他区域时会自动关闭，需要定期重新获取焦点
                focusKeeper[0] = object : Runnable {
                    override fun run() {
                        if (isCleanedUp[0] || dummyInput.parent == null) {
                            return  // 已经清理或已移除，停止检查
                        }


                        // 如果失去焦点，重新获取
                        if (!dummyInput.hasFocus()) {
                            Log.d(TAG, "定期检查发现EditText失去焦点，重新获取焦点")
                            dummyInput.requestFocus()
                            // 确保键盘仍然显示
                            val imm =
                                context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                            imm?.showSoftInput(dummyInput, InputMethodManager.SHOW_IMPLICIT)
                        }


                        // 每500ms检查一次（不要太频繁，避免影响性能）
                        if (!isCleanedUp[0] && dummyInput.parent != null) {
                            timeoutHandler.postDelayed(this, 500)
                        }
                    }
                }
                // 延迟启动，给键盘一些时间显示
                timeoutHandler.postDelayed(focusKeeper[0]!!, 300)


                // 实时发送每个字符和删除操作
                dummyInput.addTextChangedListener(object : TextWatcher {
                    private var lastText = ""

                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                        val currentText = s.toString()

                        if (currentText.length > lastText.length) {
                            // 文本增加：发送新字符
                            val newChars = currentText.substring(lastText.length)
                            GameActivity.sendTextToGame(newChars)
                        } else if (currentText.length < lastText.length) {
                            // 文本减少：发送退格符
                            val deleteCount = lastText.length - currentText.length
                            for (i in 0..<deleteCount) {
                                GameActivity.sendBackspace()
                            }
                        }

                        lastText = currentText
                    }

                    override fun afterTextChanged(s: Editable?) {}
                })


                // 回车键关闭键盘
                dummyInput.setOnEditorActionListener { v: TextView?, actionId: Int, event: KeyEvent? ->
                    if (actionId == EditorInfo.IME_ACTION_DONE ||
                        (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
                    ) {
                        rootView.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
                        cleanup.run()
                        return@setOnEditorActionListener true
                    }
                    false
                }


                // 30秒自动关闭
                timeoutRunnable[0] = Runnable {
                    rootView.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
                    cleanup.run()
                }
                timeoutHandler.postDelayed(timeoutRunnable[0]!!, 30000)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show IME", e)
            }
        }
    }
}
