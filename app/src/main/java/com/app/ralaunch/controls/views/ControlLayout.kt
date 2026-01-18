package com.app.ralaunch.controls.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.controls.bridges.ControlInputBridge
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.packs.ControlLayout as PackControlLayout
import com.app.ralaunch.utils.AppLogger
import java.io.File
import kotlin.math.sqrt

/**
 * 虚拟控制布局管理器
 * 负责管理所有虚拟控制元素的布局和显示
 *
 * 重要：所有触摸事件都会转发给 SDLSurface，确保游戏能收到完整的多点触控输入
 *
 * - 设计图基准：2560x1080px
 * - density 会在 Activity.onCreate() 中动态调整
 * - JSON 中的 px 值会自动通过 density 适配到不同屏幕
 * - 无需手动进行尺寸转换，系统会自动处理
 */
class ControlLayout : FrameLayout {
    // SDLSurface 引用，用于转发触摸事件
    private var mSDLSurface: View? = null

    private var mControls: MutableList<ControlView> = ArrayList()
    var inputBridge: ControlInputBridge? = null

    /**
     * 获取当前布局
     */
    var currentLayout: PackControlLayout? = null
        private set
    
    /**
     * 当前控件包的资源目录（用于加载纹理）
     */
    private var currentAssetsDir: File? = null
    
    private var mVisible = true
    private var mModifiable = false // 是否可编辑模式
    private var mSelectedControl: ControlView? = null // 当前选中的控件
    private var mLastTouchX = 0f
    private var mLastTouchY = 0f // 上次触摸位置
    private var mEditControlListener: EditControlListener? = null // 编辑监听器
    private var mOnControlChangedListener: OnControlChangedListener? = null // 控件修改监听器

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private fun init() {
        setWillNotDraw(false)

        // 禁用子View裁剪，让控件的绘制效果（如摇杆方向线）完整显示
        clipChildren = false
        clipToPadding = false

        // 启用硬件加速层，以支持 RippleDrawable 等需要硬件加速的动画
        setLayerType(LAYER_TYPE_HARDWARE, null)

        // 不设置 isClickable - ControlLayout 不应该消费触摸事件
        // 它只是一个容器，用于转发事件给子控件和 SDLSurface
        isFocusable = false
        isFocusableInTouchMode = false
    }

    /**
     * 设置 SDLSurface 引用，用于转发触摸事件
     */
    fun setSDLSurface(sdlSurface: View?) {
        mSDLSurface = sdlSurface
    }

    /**
     * 重写 onInterceptTouchEvent 确保每个新的触摸点独立判断是否由控件处理
     *
     * 关键：默认的 FrameLayout 行为会将整个手势序列绑定到第一个接受 ACTION_DOWN 的 View
     * 但我们需要每个 POINTER_DOWN 独立判断，允许不同手指触摸不同控件
     */
    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        // 永远不拦截 - 让所有事件都能到达子控件
        // 我们在 dispatchTouchEvent 中手动转发给 SDL
        return false
    }

    /**
     * 重写 dispatchTouchEvent 确保控件优先处理，SDLSurface 收到未处理的触摸事件
     *
     * 关键修复：FrameLayout 默认会将整个手势序列绑定到处理 ACTION_DOWN 的 View
     * 对于多点触控，我们需要每个 POINTER_DOWN 独立判断，允许不同手指触摸不同控件
     */
    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return false
        }

        // 检查是否是真实鼠标事件（而非触摸事件）
        // 真实鼠标事件应该直接传递给 SDLSurface，不被虚拟控件拦截
        //
        // 鼠标事件的判断方式：
        // 1. source == SOURCE_MOUSE (纯鼠标)
        // 2. source == SOURCE_MOUSE | SOURCE_TOUCHSCREEN (Samsung DeX 模式等)
        // 3. toolType == TOOL_TYPE_MOUSE (更可靠的判断方式)
        val source = event.source
        val isMouseSource = source == android.view.InputDevice.SOURCE_MOUSE ||
                source == (android.view.InputDevice.SOURCE_MOUSE or android.view.InputDevice.SOURCE_TOUCHSCREEN)
        val isMouseTool = event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE

        if (isMouseSource || isMouseTool) {
            // 鼠标事件，直接传递给 SDLSurface
            if (mSDLSurface != null) {
                return mSDLSurface!!.dispatchTouchEvent(event)
            }
            return false
        }

        val action = event.actionMasked
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)

        // Debug logging
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            AppLogger.debug(TAG, "dispatchTouchEvent: action=${actionName(action)} pointerId=$pointerId " +
                "x=${event.getX(actionIndex)} y=${event.getY(actionIndex)} modifiable=$mModifiable childCount=$childCount")

            // Log all children
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val childRect = android.graphics.Rect()
                child.getHitRect(childRect)
                AppLogger.debug(TAG, "  Child $i: ${child.javaClass.simpleName} " +
                    "bounds=[${childRect.left},${childRect.top},${childRect.right},${childRect.bottom}] " +
                    "visibility=${child.visibility} clickable=${child.isClickable} hasOnTouchListener=${child.hasOnClickListeners()}")
            }
        }

        // 编辑模式下，只处理控件，不转发给 SDLSurface
        if (mModifiable) {
            return super.dispatchTouchEvent(event)
        }

        // 对于多点触控相关事件，手动检查并分发到所有子控件
        // 这绕过了 FrameLayout 的默认行为（将手势绑定到第一个处理 ACTION_DOWN 的 View）
        when (action) {
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP -> {
                // 新指针按下/抬起 - 需要检查该指针位置的控件
                val x = event.getX(actionIndex)
                val y = event.getY(actionIndex)

                AppLogger.debug(TAG, "dispatchTouchEvent: Manually checking ${actionName(action)} for children")

                var anyHandled = false

                // 从后往前遍历（后添加的在上层）
                // 重要：不能在第一个控件处理后就返回，因为多个控件可能都在该位置
                // 例如：一个按钮和一个摇杆重叠，两者都应该有机会接收事件
                for (i in childCount - 1 downTo 0) {
                    val child = getChildAt(i)
                    if (child.visibility != VISIBLE) {
                        continue
                    }

                    // 检查触摸点是否在子视图的边界内
                    val childRect = android.graphics.Rect()
                    child.getHitRect(childRect)

                    if (childRect.contains(x.toInt(), y.toInt())) {
                        AppLogger.debug(TAG, "  Touch in bounds of child $i, dispatching")

                        // 创建一个坐标转换后的新事件，避免修改原事件影响其他指针
                        // 使用 offsetLocation 而不是 setLocation，因为 setLocation 只修改主指针(pointer 0)
                        // 而 offsetLocation 会正确偏移所有指针的坐标
                        val localEvent = MotionEvent.obtain(event)
                        localEvent.offsetLocation(-childRect.left.toFloat(), -childRect.top.toFloat())

                        AppLogger.debug(TAG, "  Transformed coords: parent($x, $y) -> local(${x - childRect.left}, ${y - childRect.top})")

                        // 手动分发事件到子视图（使用本地坐标）
                        val handled = child.dispatchTouchEvent(localEvent)

                        // 回收临时事件
                        localEvent.recycle()

                        AppLogger.debug(TAG, "  Child $i returned $handled")

                        if (handled) {
                            anyHandled = true
                            // 不要在这里 return！继续检查其他控件
                            // 这样多个控件可以同时响应不同的手指
                        }
                    }
                }

                // 转发给 SDL（SDL 会过滤已消费的指针）
                if (mSDLSurface != null) {
                    mSDLSurface!!.dispatchTouchEvent(event)
                }

                if (!anyHandled) {
                    AppLogger.debug(TAG, "dispatchTouchEvent: No child handled ${actionName(action)}")
                }

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // MOVE 事件包含所有活动指针的位置
                // 需要将事件分发给所有可能正在跟踪指针的控件
                var anyChildHandled = false

                // 遍历所有子控件，让它们有机会处理 MOVE 事件
                for (i in childCount - 1 downTo 0) {
                    val child = getChildAt(i)
                    if (child.visibility != VISIBLE) {
                        continue
                    }

                    val childRect = android.graphics.Rect()
                    child.getHitRect(childRect)

                    // 为该子控件创建坐标转换后的事件
                    // MOVE 事件比较特殊，包含多个指针，我们需要转换所有指针坐标
                    val localEvent = MotionEvent.obtain(event)
                    localEvent.offsetLocation(-childRect.left.toFloat(), -childRect.top.toFloat())

                    val handled = child.dispatchTouchEvent(localEvent)
                    localEvent.recycle()

                    if (handled) {
                        anyChildHandled = true
                    }
                }

                // 转发给 SDL（SDL 会过滤已消费的指针）
                if (mSDLSurface != null) {
                    mSDLSurface!!.dispatchTouchEvent(event)
                }

                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                // 取消事件需要通知所有子控件清理状态
                for (i in 0 until childCount) {
                    val child = getChildAt(i)
                    if (child.visibility == VISIBLE) {
                        val childRect = android.graphics.Rect()
                        child.getHitRect(childRect)

                        val localEvent = MotionEvent.obtain(event)
                        localEvent.offsetLocation(-childRect.left.toFloat(), -childRect.top.toFloat())

                        child.dispatchTouchEvent(localEvent)
                        localEvent.recycle()
                    }
                }

                // 转发给 SDL
                if (mSDLSurface != null) {
                    mSDLSurface!!.dispatchTouchEvent(event)
                }

                return true
            }

            MotionEvent.ACTION_DOWN -> {
                // 第一个手指按下 - 手动分发到所有可能的控件
                val x = event.getX(actionIndex)
                val y = event.getY(actionIndex)

                AppLogger.debug(TAG, "dispatchTouchEvent: Manually checking ACTION_DOWN for children")

                var anyHandled = false

                // 从后往前遍历（后添加的在上层）
                for (i in childCount - 1 downTo 0) {
                    val child = getChildAt(i)
                    if (child.visibility != VISIBLE) {
                        continue
                    }

                    val childRect = android.graphics.Rect()
                    child.getHitRect(childRect)

                    if (childRect.contains(x.toInt(), y.toInt())) {
                        AppLogger.debug(TAG, "  Touch in bounds of child $i, dispatching ACTION_DOWN")

                        val localEvent = MotionEvent.obtain(event)
                        localEvent.offsetLocation(-childRect.left.toFloat(), -childRect.top.toFloat())

                        val handled = child.dispatchTouchEvent(localEvent)
                        localEvent.recycle()

                        AppLogger.debug(TAG, "  Child $i returned $handled")

                        if (handled) {
                            anyHandled = true
                            // 对于 DOWN，找到第一个处理的控件后可以停止
                            // 但为了一致性，继续检查其他控件
                        }
                    }
                }

                // 转发给 SDL
                if (mSDLSurface != null) {
                    mSDLSurface!!.dispatchTouchEvent(event)
                }

                return true
            }

            MotionEvent.ACTION_UP -> {
                // 最后一个手指抬起 - 需要通知所有控件，让它们检查是否是自己跟踪的指针
                AppLogger.debug(TAG, "dispatchTouchEvent: Broadcasting ACTION_UP to all children")

                for (i in 0 until childCount) {
                    val child = getChildAt(i)
                    if (child.visibility != VISIBLE) {
                        continue
                    }

                    val childRect = android.graphics.Rect()
                    child.getHitRect(childRect)

                    val localEvent = MotionEvent.obtain(event)
                    localEvent.offsetLocation(-childRect.left.toFloat(), -childRect.top.toFloat())

                    child.dispatchTouchEvent(localEvent)
                    localEvent.recycle()
                }

                // 转发给 SDL
                if (mSDLSurface != null) {
                    mSDLSurface!!.dispatchTouchEvent(event)
                }

                return true
            }
        }

        // 对于其他事件，使用默认行为（不应该走到这里，上面已经处理了所有主要事件）
        val handled = super.dispatchTouchEvent(event)

        AppLogger.debug(TAG, "dispatchTouchEvent: Fallback to super for action ${actionName(action)}, handled=$handled")

        // 转发给 SDLSurface
        if (mSDLSurface != null) {
            mSDLSurface!!.dispatchTouchEvent(event)
        }

        // 总是返回 true，因为我们处理了事件（转发给控件或SDL）
        return true
    }

    private fun actionName(action: Int): String {
        return when (action) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_POINTER_DOWN -> "POINTER_DOWN"
            MotionEvent.ACTION_POINTER_UP -> "POINTER_UP"
            MotionEvent.ACTION_CANCEL -> "CANCEL"
            else -> "UNKNOWN($action)"
        }
    }

    /**
     * 加载控制布局配置
     * @return 是否成功加载布局
     */
    fun loadLayout(layout: PackControlLayout?): Boolean {
        if (inputBridge == null) {
            AppLogger.error(TAG, "InputBridge not set! Call setInputBridge() first.")
            return false
        }

        this.currentLayout = layout

        if (layout == null) {
            AppLogger.warn(TAG, "Layout is null")
            return false
        }

        // 创建虚拟控制元素
        val controlViews = layout.controls
            .map { Pair(createControlView(it), it) }
            .filter { it.first != null }
            .toTypedArray()

        // 清除现有控件视图
        clearControls()

        if (controlViews.isEmpty()) {
            AppLogger.warn(TAG, "No visible controls were added, layout may appear empty")
            return false
        }

        // 添加新的控件视图
        controlViews.forEach { (controlView, data) ->
            addControlView(controlView, data)
        }
        AppLogger.debug(TAG, "Loaded " + controlViews.size + " controls from layout: " + layout.name)
        return true
    }

    /**
     * 从 ControlPackManager 加载当前选中的控制布局
     * @return 是否成功加载布局
     */
    fun loadLayoutFromPackManager(): Boolean {
        val packManager = RaLaunchApplication.getControlPackManager()
        val packId = packManager.getSelectedPackId()
        val layout = packManager.getCurrentLayout()
        
        if (layout == null || packId == null) {
            AppLogger.warn(TAG, "No current layout selected in pack manager")
            return false
        }
        
        // 获取控件包的资源目录
        currentAssetsDir = packManager.getPackAssetsDir(packId)
        
        return loadLayout(layout)
    }
    
    /**
     * 设置控件包资源目录（用于加载纹理）
     */
    fun setPackAssetsDir(dir: File?) {
        currentAssetsDir = dir
        // 更新所有现有控件的资源目录
        mControls.forEach { controlView ->
            controlView.setPackAssetsDir(dir)
        }
    }

    /**
     * 创建控制View
     */
    private fun createControlView(data: ControlData?): ControlView? {
        when (data) {
            is ControlData.Button -> {
                return VirtualButton(context, data, inputBridge!!)
            }
            is ControlData.Joystick -> {
                return VirtualJoystick(context, data, inputBridge!!)
            }
            is ControlData.TouchPad -> {
                return VirtualTouchPad(context, data, inputBridge!!)
            }
            is ControlData.Text -> {
                return VirtualText(context, data, inputBridge!!)
            }
            else -> {
                AppLogger.warn(TAG, "Unknown control type")
                return null
            }
        }
    }

    /**
     * 添加控制View到布局
     */
    private fun addControlView(controlView: ControlView?, data: ControlData) {
        val view = controlView as View
        
        // 设置控件包资源目录（用于加载纹理）
        controlView.setPackAssetsDir(currentAssetsDir)

        // 不设置 isClickable - 让控件通过 onTouchEvent 自行决定是否处理触摸
        // 设置为 clickable 会导致 View 捕获整个触摸序列，即使触摸在形状外
        view.isFocusable = false
        view.isFocusableInTouchMode = false

        // 强制确保没有 OnTouchListener（调试用）
        view.setOnTouchListener(null)
        AppLogger.debug(TAG, "addControlView: ${data.name} - removed any existing OnTouchListener")

        val params = LayoutParams(
            widthToPx(data.width),
            heightToPx(data.height)
        )
        params.leftMargin = xToPx(data.x)
        params.topMargin = yToPx(data.y)

        view.layoutParams = params

        setupEditModeListeners(view, controlView, data)

        addView(view)
        mControls.add(controlView)
    }

    /**
     * 设置编辑模式的触摸监听器
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupEditModeListeners(view: View, controlView: ControlView?, data: ControlData) {
        val DRAG_THRESHOLD = 15f
        val state = object {
            var downPosX = 0f
            var downPosY = 0f
            var isDragging = false
        }

        val touchListener = View.OnTouchListener { v: View, event: MotionEvent ->
            if (!mModifiable) {
                // 在非编辑模式下，不应该有这个 listener，但为了安全，返回 false
                return@OnTouchListener false
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mSelectedControl = controlView
                    state.downPosX = event.rawX
                    state.downPosY = event.rawY
                    mLastTouchX = event.rawX
                    mLastTouchY = event.rawY
                    state.isDragging = false

                    v.alpha = 0.5f
                    return@OnTouchListener true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (mSelectedControl === controlView) {
                        val deltaX = event.rawX - mLastTouchX
                        val deltaY = event.rawY - mLastTouchY

                        val totalDx = event.rawX - state.downPosX
                        val totalDy = event.rawY - state.downPosY
                        val totalDistance = sqrt(totalDx * totalDx + totalDy * totalDy)

                        if (totalDistance > DRAG_THRESHOLD) {
                            state.isDragging = true
                        }

                        if (state.isDragging) {
                            val params = v.layoutParams as LayoutParams
                            params.leftMargin += deltaX.toInt()
                            params.topMargin += deltaY.toInt()
                            v.layoutParams = params

                            data.x = xFromPx(params.leftMargin)
                            data.y = yFromPx(params.topMargin)

                            mLastTouchX = event.rawX
                            mLastTouchY = event.rawY
                        }
                    }
                    return@OnTouchListener true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1.0f

                    if (controlView is VirtualButton) {
                        controlView.isPressedState = false
                    }

                    val finalDx = event.rawX - state.downPosX
                    val finalDy = event.rawY - state.downPosY
                    val finalDistance =
                        sqrt((finalDx * finalDx + finalDy * finalDy).toDouble()).toFloat()

                    if (finalDistance <= DRAG_THRESHOLD && !state.isDragging) {
                        mEditControlListener?.onEditControl(data)
                    } else {
                        mOnControlChangedListener?.onControlChanged()
                    }

                    mSelectedControl = null
                    return@OnTouchListener true
                }
            }
            false
        }

        // 只在编辑模式下设置 OnTouchListener
        // 在非编辑模式下，OnTouchListener 会干扰正常的 onTouchEvent 分发
        if (mModifiable) {
            view.setOnTouchListener(touchListener)
        } else {
            view.setOnTouchListener(null)
        }
    }

    /**
     * 清除所有控制元素
     */
    fun clearControls() {
        removeAllViews()
        mControls.clear()
    }

    var isControlsVisible: Boolean
        get() = mVisible
        set(visible) {
            mVisible = visible
            setVisibility(if (visible) VISIBLE else GONE)
        }

    /**
     * 切换控制布局显示状态
     */
    fun toggleControlsVisible() {
        this.isControlsVisible = !mVisible
    }

    /**
     * 重置所有切换按钮状态
     */
    fun resetAllToggles() {
        for (control in mControls) {
            if (control is VirtualButton) {
                control.resetToggle()
            }
        }
    }

    var isModifiable: Boolean
        get() = mModifiable
        set(modifiable) {
            mModifiable = modifiable

            if (this.currentLayout != null) {
                for (i in mControls.indices) {
                    val controlView = mControls[i]
                    val data = currentLayout!!.controls[i]
                    val view = controlView as View
                    setupEditModeListeners(view, controlView, data)
                }
            }
        }

    fun setEditControlListener(listener: EditControlListener?) {
        mEditControlListener = listener
    }

    fun setOnControlChangedListener(listener: OnControlChangedListener?) {
        mOnControlChangedListener = listener
    }

    fun interface EditControlListener {
        fun onEditControl(data: ControlData?)
    }

    fun interface OnControlChangedListener {
        fun onControlChanged()
    }

    private fun xToPx(value: Float): Int {
        return (value * resources.displayMetrics.widthPixels).toInt()
    }

    private fun yToPx(value: Float): Int {
        return (value * resources.displayMetrics.heightPixels).toInt()
    }

    private fun widthToPx(value: Float): Int {
        return (value * resources.displayMetrics.heightPixels).toInt()
    }

    private fun heightToPx(value: Float): Int {
        return (value * resources.displayMetrics.heightPixels).toInt()
    }

    private fun xFromPx(px: Int): Float {
        return px.toFloat() / resources.displayMetrics.widthPixels
    }

    private fun yFromPx(px: Int): Float {
        return px.toFloat() / resources.displayMetrics.heightPixels
    }

    companion object {
        private const val TAG = "ControlLayout"
    }
}
