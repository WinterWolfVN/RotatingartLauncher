package com.app.ralaunch.controls.editors

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.app.ralaunch.R
import com.app.ralaunch.controls.configs.ControlConfig
import com.app.ralaunch.controls.configs.ControlData
import com.app.ralaunch.controls.views.ControlLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 统一的控件编辑管理器
 *
 * 支持两种模式：
 * 1. MODE_STANDALONE (游戏外): 直接进入编辑模式，用于独立的控件编辑器界面
 * 2. MODE_IN_GAME (游戏内): 需要手动进入编辑模式，用于游戏运行时的编辑
 */
class ControlEditorManager(
    private val mContext: Context, private var mControlLayout: ControlLayout?,
    private val mContentFrame: ViewGroup?,
    /**
     * 获取当前模式
     */
    var mode: Mode
) {
    private val mScreenWidth: Int
    private val mScreenHeight: Int

    private var mIsInEditor = false
    private var mHasUnsavedChanges = false

    private var mControlEditDialog: ControlEditDialogMD? = null
    private var mEditorSettingsDialog: UnifiedEditorSettingsDialog? = null

    private var mStateListener: OnEditorStateChangedListener? = null
    private var mLayoutChangedListener: OnLayoutChangedListener? = null
    private var mFPSDisplayListener: OnFPSDisplayChangedListener? = null
    private var mOnHideControlsListener: OnHideControlsListener? = null
    private var mToggleTouchEventListener: OnToggleTouchEventChangedListener? = null
    private var mOnExitGameListener: OnExitGameListener? = null

    enum class Mode {
        STANDALONE, /** 独立模式：直接进入编辑模式  */
        IN_GAME     /** 游戏内模式：需要手动进入编辑模式  */
    }

    /**
     * 编辑状态变化监听器
     */
    interface OnEditorStateChangedListener {
        fun onEditorEntered()
        fun onEditorExited()
    }

    /**
     * 布局变化监听器（用于 Activity 刷新显示）
     */
    interface OnLayoutChangedListener {
        fun onLayoutChanged()
    }

    /**
     * FPS 显示设置变化监听器
     */
    interface OnFPSDisplayChangedListener {
        fun onFPSDisplayChanged(enabled: Boolean)
    }

    /**
     * 隐藏控件监听器
     */
    interface OnHideControlsListener {
        fun onHideControls()
    }

    /**
     * 触摸传递事件设置变化监听器
     */
    interface OnToggleTouchEventChangedListener {
        fun onToggleTouchEventChanged(enabled: Boolean)
    }

    /**
     * 退出游戏监听器
     */
    interface OnExitGameListener {
        fun onExitGame()
    }

    /**
     * 创建控件编辑管理器
     * @param mContext 上下文
     * @param mControlLayout 控件布局
     * @param mContentFrame 内容容器（用于显示对话框）
     * @param mode 模式：MODE_STANDALONE 或 MODE_IN_GAME
     */
    init {
        val metrics = mContext.resources.displayMetrics
        mScreenWidth = metrics.widthPixels
        mScreenHeight = metrics.heightPixels

        // 独立模式下自动进入编辑模式
        if (this.mode == Mode.STANDALONE) {
            setupEditMode()
        }
    }

    /**
     * 设置编辑状态变化监听器
     */
    fun setOnEditorStateChangedListener(listener: OnEditorStateChangedListener?) {
        mStateListener = listener
    }

    /**
     * 设置布局变化监听器
     */
    fun setOnLayoutChangedListener(listener: OnLayoutChangedListener?) {
        mLayoutChangedListener = listener
    }

    /**
     * 设置 FPS 显示变化监听器
     */
    fun setOnFPSDisplayChangedListener(listener: OnFPSDisplayChangedListener?) {
        mFPSDisplayListener = listener
    }

    /**
     * 设置隐藏控件监听器
     */
    fun setOnHideControlsListener(listener: OnHideControlsListener?) {
        mOnHideControlsListener = listener
    }

    /**
     * 设置触摸事件传递变化监听器
     */
    fun setOnToggleTouchEventChangedListener(listener: OnToggleTouchEventChangedListener?) {
        mToggleTouchEventListener = listener
    }

    /**
     * 设置退出游戏监听器
     */
    fun setOnExitGameListener(listener: OnExitGameListener?) {
        mOnExitGameListener = listener
    }

    /**
     * 设置控件布局（用于布局重新创建后更新引用）
     */
    fun setControlLayout(controlLayout: ControlLayout?) {
        mControlLayout = controlLayout
        // 独立模式下始终自动进入编辑模式
        if (this.mode == Mode.STANDALONE) {
            setupEditMode()
        } else if (mIsInEditor) {
            // 游戏内模式：只有在已进入编辑模式时才重新设置
            setupEditMode()
        }
    }

    /**
     * 配置编辑模式（设置监听器等）
     */
    private fun setupEditMode() {
        if (mControlLayout == null) return

        // 独立模式下自动设置为编辑状态
        if (this.mode == Mode.STANDALONE) {
            mIsInEditor = true
        }

        mControlLayout!!.isModifiable = true

        // 初始化编辑对话框
        initControlEditDialog()

        // 设置控件点击监听器
        mControlLayout!!.setEditControlListener { data: ControlData? ->
            if (mControlEditDialog != null && mContext is FragmentActivity) {
                val activity = mContext
                mControlEditDialog!!.showWithData(
                    activity.supportFragmentManager,
                    "control_edit",
                    data
                )
            }
        }

        // 设置控件修改监听器（拖动时）
        mControlLayout!!.setOnControlChangedListener {
            mHasUnsavedChanges = true
        }

        // 禁用视图裁剪
        disableClippingRecursive(mControlLayout!!)
    }

    /**
     * 进入编辑模式（仅游戏内模式使用）
     */
    fun enterEditMode() {
        if (mControlLayout == null) return
        if (this.mode == MODE_STANDALONE) return  // 独立模式始终处于编辑状态


        mIsInEditor = true
        mHasUnsavedChanges = false

        setupEditMode()

        // 初始化编辑器设置弹窗
        initEditorSettingsDialog()

        // 设置编辑模式为启用状态
        if (mEditorSettingsDialog != null) {
            mEditorSettingsDialog.setEditModeEnabled(true)
        }

        // 确保控制可见
        mControlLayout!!.isControlsVisible = true

        Toast.makeText(mContext, R.string.editor_mode_on, Toast.LENGTH_SHORT).show()

        if (mStateListener != null) {
            mStateListener!!.onEditorEntered()
        }
    }

    /**
     * 退出编辑模式（仅游戏内模式使用）
     */
    fun exitEditMode() {
        if (this.mode == Mode.STANDALONE) return  // 独立模式不支持退出


        // 如果有未保存的修改，弹出确认对话框
        if (mHasUnsavedChanges) {
            AlertDialog.Builder(mContext)
                .setTitle(R.string.editor_exit_confirm)
                .setMessage(R.string.editor_exit_message)
                .setPositiveButton(
                    R.string.game_menu_yes
                ) { dialog: DialogInterface?, which: Int ->
                    performExitEditMode()
                }
                .setNegativeButton(R.string.game_menu_no, null)
                .show()
        } else {
            performExitEditMode()
        }
    }

    /**
     * 执行退出编辑模式
     */
    private fun performExitEditMode() {
        if (mControlLayout == null) return

        mIsInEditor = false
        mControlLayout!!.isModifiable = false

        // 设置编辑模式为禁用状态
        if (mEditorSettingsDialog != null) {
            mEditorSettingsDialog.setEditModeEnabled(false)
        }

        // 重新加载布局
        mControlLayout!!.loadLayoutFromManager()

        // 禁用视图裁剪
        disableClippingRecursive(mControlLayout!!)

        Toast.makeText(mContext, R.string.editor_mode_off, Toast.LENGTH_SHORT).show()

        if (mStateListener != null) {
            mStateListener!!.onEditorExited()
        }
    }

    /**
     * 切换编辑模式
     */
    fun toggleEditMode() {
        if (this.mode == Mode.STANDALONE) return

        if (mIsInEditor) {
            exitEditMode()
        } else {
            enterEditMode()
        }
    }

    /**
     * 初始化控件编辑对话框
     */
    private fun initControlEditDialog() {
        if (mControlEditDialog != null) return

        // 使用 DialogFragment 的静态工厂方法创建
        mControlEditDialog = ControlEditDialogMD.newInstance(mScreenWidth, mScreenHeight)

        // 设置实时更新监听器
        mControlEditDialog.setOnControlUpdatedListener({ control ->
            if (mControlLayout != null) {
                ControlDataSyncManager.syncControlDataToView(mControlLayout, control)
                mHasUnsavedChanges = true
            }
        })

        // 设置删除监听器
        mControlEditDialog.setOnControlDeletedListener({ control ->
            if (mControlLayout != null) {
                val config = mControlLayout!!.config
                if (config != null && config.controls != null) {
                    // 遍历列表找到相同的引用并删除
                    for (i in config.controls.indices.reversed()) {
                        if (config.controls.get(i) === control) {
                            config.controls.removeAt(i)
                            break
                        }
                    }
                    mControlLayout!!.loadLayout(config)
                    disableClippingRecursive(mControlLayout!!)
                    mHasUnsavedChanges = true

                    if (mLayoutChangedListener != null) {
                        mLayoutChangedListener!!.onLayoutChanged()
                    }
                }
            }
        })

        // 设置复制监听器
        mControlEditDialog.setOnControlCopiedListener({ control ->
            if (mControlLayout != null) {
                val config = mControlLayout!!.config
                if (config != null && config.controls != null) {
                    config.controls.add(control)
                    mControlLayout!!.loadLayout(config)
                    disableClippingRecursive(mControlLayout!!)
                    mHasUnsavedChanges = true

                    if (mLayoutChangedListener != null) {
                        mLayoutChangedListener!!.onLayoutChanged()
                    }
                }
            }
        })
    }

    /**
     * 初始化编辑器设置弹窗
     */
    fun initEditorSettingsDialog() {
        if (mEditorSettingsDialog != null) return

        val dialogMode: UnifiedEditorSettingsDialog.DialogMode? =
            if (this.mode == Mode.STANDALONE) UnifiedEditorSettingsDialog.DialogMode.EDITOR else UnifiedEditorSettingsDialog.DialogMode.GAME

        mEditorSettingsDialog = UnifiedEditorSettingsDialog(
            mContext, mContentFrame, mScreenWidth, dialogMode
        )

        mEditorSettingsDialog.setOnMenuItemClickListener(object : OnMenuItemClickListener() {
            public override fun onAddButton() {
                addButton()
            }

            public override fun onAddJoystick() {
                addJoystick()
            }

            public override fun onAddTouchPad() {
                addTouchPad()
            }

            public override fun onAddText() {
                addText()
            }

            public override fun onToggleEditMode() {
                toggleEditMode()
            }

            public override fun onSaveLayout() {
                saveLayout()
                Toast.makeText(
                    mContext,
                    mContext.getString(R.string.editor_layout_saved),
                    Toast.LENGTH_SHORT
                ).show()
            }

            public override fun onFPSDisplayChanged(enabled: Boolean) {
                // FPS 显示设置变化
                if (mFPSDisplayListener != null) {
                    mFPSDisplayListener!!.onFPSDisplayChanged(enabled)
                }
            }

            public override fun onHideControls() {
                // 隐藏控件
                if (mOnHideControlsListener != null) {
                    mOnHideControlsListener!!.onHideControls()
                }
            }

            public override fun onToggleTouchEventChanged(enabled: Boolean) {
                // 触摸事件传递切换
                if (mToggleTouchEventListener != null) {
                    mToggleTouchEventListener!!.onToggleTouchEventChanged(enabled)
                }
            }

            public override fun onExitGame() {
                // 退出游戏
                if (mOnExitGameListener != null) {
                    mOnExitGameListener!!.onExitGame()
                }
            }
        })

        // 独立模式下设置编辑模式为启用状态
        if (this.mode == Mode.STANDALONE) {
            mEditorSettingsDialog.setEditModeEnabled(true)
        }
    }

    /**
     * 显示设置对话框
     */
    fun showSettingsDialog() {
        if (mEditorSettingsDialog == null) {
            initEditorSettingsDialog()
        }
        // 独立模式下始终确保编辑模式为启用状态
        if (this.mode == Mode.STANDALONE) {
            mEditorSettingsDialog.setEditModeEnabled(true)
        } else {
            mEditorSettingsDialog.setEditModeEnabled(mIsInEditor)
        }
        mEditorSettingsDialog.show()
        // 对话框显示后再次确保状态正确（因为布局可能被重新创建）
        if (this.mode == MODE_STANDALONE) {
            mEditorSettingsDialog.setEditModeEnabled(true)
        }
    }

    /**
     * 添加按钮
     */
    fun addButton() {
        if (mControlLayout == null) return

        var config = mControlLayout!!.config
        if (config == null) {
            config = ControlConfig()
            config.controls = ArrayList<ControlData>()
            mControlLayout!!.loadLayout(config)
        }

        val button: ControlData =
            ControlEditorOperations.addButton(mContext, config, mScreenWidth, mScreenHeight)

        mControlLayout!!.loadLayout(config)
        disableClippingRecursive(mControlLayout!!)
        mHasUnsavedChanges = true
        Toast.makeText(
            mContext,
            mContext.getString(R.string.editor_button_added),
            Toast.LENGTH_SHORT
        ).show()

        if (mLayoutChangedListener != null) {
            mLayoutChangedListener!!.onLayoutChanged()
        }
    }

    /**
     * 添加摇杆
     */
    fun addJoystick() {
        if (mControlLayout == null) return

        val config = mControlLayout!!.config
        val finalConfig: ControlConfig?
        if (config == null) {
            finalConfig = ControlConfig()
            finalConfig.controls = ArrayList<ControlData>()
            mControlLayout!!.loadLayout(finalConfig)
        } else {
            finalConfig = config
        }

        // 第一步：选择摇杆类型
        val joystickTypeOptions: Array<String?> = arrayOf<String>(
            mContext.getString(R.string.editor_joystick_type_move_aim),
            mContext.getString(R.string.editor_joystick_type_gamepad)
        )

        MaterialAlertDialogBuilder(mContext)
            .setTitle(mContext.getString(R.string.editor_select_joystick_type))
            .setItems(
                joystickTypeOptions
            ) { dialog: DialogInterface?, which: Int ->
                if (which == 0) {
                    // 移动+瞄准摇杆：直接创建键盘左摇杆和鼠标右摇杆
                    val leftJoystick: ControlData? = ControlEditorOperations.addJoystick(
                        finalConfig, mScreenWidth, mScreenHeight,
                        ControlData.JOYSTICK_MODE_KEYBOARD, false
                    )
                    val rightJoystick: ControlData? = ControlEditorOperations.addJoystick(
                        finalConfig, mScreenWidth, mScreenHeight,
                        ControlData.JOYSTICK_MODE_MOUSE, true
                    )

                    if (leftJoystick != null && rightJoystick != null) {
                        mControlLayout!!.loadLayout(finalConfig)
                        disableClippingRecursive(mControlLayout!!)
                        mHasUnsavedChanges = true
                        Toast.makeText(
                            mContext,
                            mContext.getString(R.string.editor_joystick_added),
                            Toast.LENGTH_SHORT
                        ).show()

                        if (mLayoutChangedListener != null) {
                            mLayoutChangedListener!!.onLayoutChanged()
                        }
                    }
                } else {
                    // 手柄摇杆模式：选择左摇杆还是右摇杆
                    val joystickMode: Int = ControlData.JOYSTICK_MODE_SDL_CONTROLLER
                    showStickSideDialog(finalConfig, joystickMode)
                }
            }
            .show()
    }

    /**
     * 添加触控板
     */
    fun addTouchPad() {
        if (mControlLayout == null) return

        var config = mControlLayout!!.config
        if (config == null) {
            config = ControlConfig()
            config.controls = ArrayList<ControlData?>()
            mControlLayout!!.loadLayout(config)
        }

        val touchpad: ControlData? =
            ControlEditorOperations.addTouchPad(mContext, config, mScreenWidth, mScreenHeight)

        if (touchpad != null) {
            mControlLayout!!.loadLayout(config)
            disableClippingRecursive(mControlLayout!!)
            mHasUnsavedChanges = true
            Toast.makeText(
                mContext,
                mContext.getString(R.string.editor_touchpad_added),
                Toast.LENGTH_SHORT
            ).show()

            if (mLayoutChangedListener != null) {
                mLayoutChangedListener!!.onLayoutChanged()
            }
        }
    }

    /**
     * 显示选择摇杆位置的对话框
     */
    private fun showStickSideDialog(finalConfig: ControlConfig?, joystickMode: Int) {
        val stickSideOptions: Array<String?> = arrayOf<String>(
            mContext.getString(R.string.editor_joystick_side_left),
            mContext.getString(R.string.editor_joystick_side_right)
        )

        MaterialAlertDialogBuilder(mContext)
            .setTitle(mContext.getString(R.string.editor_select_joystick_side))
            .setItems(
                stickSideOptions
            ) { dialog2: DialogInterface?, which2: Int ->
                val isRightStick = (which2 == 1)
                // 创建摇杆
                val joystick: ControlData? = ControlEditorOperations.addJoystick(
                    finalConfig, mScreenWidth, mScreenHeight, joystickMode, isRightStick
                )
                if (joystick != null) {
                    mControlLayout!!.loadLayout(finalConfig)
                    disableClippingRecursive(mControlLayout!!)
                    mHasUnsavedChanges = true
                    Toast.makeText(
                        mContext,
                        mContext.getString(R.string.editor_joystick_added),
                        Toast.LENGTH_SHORT
                    ).show()

                    if (mLayoutChangedListener != null) {
                        mLayoutChangedListener!!.onLayoutChanged()
                    }
                }
            }
            .show()
    }

    /**
     * 添加文本控件
     */
    fun addText() {
        if (mControlLayout == null) return

        var config = mControlLayout!!.config
        if (config == null) {
            config = ControlConfig()
            config.controls = ArrayList<ControlData?>()
            mControlLayout!!.loadLayout(config)
        }

        val text: ControlData? =
            ControlEditorOperations.addText(mContext, config, mScreenWidth, mScreenHeight)

        if (text != null) {
            mControlLayout!!.loadLayout(config)
            disableClippingRecursive(mControlLayout!!)
            mHasUnsavedChanges = true
            Toast.makeText(
                mContext,
                mContext.getString(R.string.editor_text_added),
                Toast.LENGTH_SHORT
            ).show()

            if (mLayoutChangedListener != null) {
                mLayoutChangedListener!!.onLayoutChanged()
            }
        }
    }

    /**
     * 保存布局
     */
    fun saveLayout() {
        if (mControlLayout == null) return

        val config = mControlLayout!!.config
        val manager: com.app.ralaunch.utils.ControlLayoutManager =
            ControlLayoutManager(mContext)
        val layoutName: String? = manager.getCurrentLayoutName()

        if (ControlEditorOperations.saveLayout(mContext, config, layoutName)) {
            mHasUnsavedChanges = false
        }
    }

    /**
     * 使用指定名称保存布局
     */
    fun saveLayout(layoutName: String?) {
        if (mControlLayout == null) return

        val config = mControlLayout!!.config
        if (ControlEditorOperations.saveLayout(mContext, config, layoutName)) {
            mHasUnsavedChanges = false
        }
    }

    /**
     * 重置为默认布局
     */
    fun resetToDefaultLayout() {
        ControlEditorOperations.resetToDefaultLayout(mContext, mControlLayout, {
            disableClippingRecursive(mControlLayout!!)
            mHasUnsavedChanges = true
            if (mLayoutChangedListener != null) {
                mLayoutChangedListener!!.onLayoutChanged()
            }
        })
    }

    /**
     * 隐藏编辑器设置对话框
     */
    fun hideSettingsDialog() {
        if (mEditorSettingsDialog != null) {
            mEditorSettingsDialog.hide()
        }
    }

    val isSettingsDialogShowing: Boolean
        /**
         * 设置对话框是否正在显示
         */
        get() = mEditorSettingsDialog != null && mEditorSettingsDialog.isDisplaying()

    val isEditDialogShowing: Boolean
        /**
         * 编辑对话框是否正在显示
         */
        get() = mControlEditDialog != null && mControlEditDialog.isAdded()

    /**
     * 关闭编辑对话框
     */
    fun dismissEditDialog() {
        if (mControlEditDialog != null) {
            mControlEditDialog.dismiss()
        }
    }

    val isInEditor: Boolean
        /**
         * 是否处于编辑模式
         */
        get() = this.mode == MODE_STANDALONE || mIsInEditor

    /**
     * 是否有未保存的修改
     */
    fun hasUnsavedChanges(): Boolean {
        return mHasUnsavedChanges
    }

    /**
     * 递归禁用所有子视图的裁剪
     */
    private fun disableClippingRecursive(view: View) {
        if (view is ViewGroup) {
            val viewGroup = view
            viewGroup.clipChildren = false
            viewGroup.clipToPadding = false

            for (i in 0..<viewGroup.childCount) {
                disableClippingRecursive(viewGroup.getChildAt(i))
            }
        }

        view.clipToOutline = false
        view.clipBounds = null
    }

    companion object {
        private const val TAG = "ControlEditorManager"
    }
}

