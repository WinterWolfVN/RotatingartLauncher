package com.app.ralaunch.controls.editors

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.widget.Toast
import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.controls.configs.ControlConfig
import com.app.ralaunch.controls.configs.ControlData
import com.app.ralaunch.controls.configs.ControlData.Joystick
import com.app.ralaunch.controls.configs.ControlData.KeyCode
import com.app.ralaunch.controls.configs.ControlData.TouchPad

/**
 * 控件编辑器操作类
 *
 * 统一管理控件编辑器的共同操作，包括：
 * - 添加按钮/摇杆/触摸板/文本
 * - 保存/加载布局
 * - 摇杆模式设置
 *
 * 使用新的 ControlConfig/ControlData 密封类 API
 */
object ControlEditorOperations {
    val context: Context
        get() = RaLaunchApplication.getAppContext()

    /**
     * 添加按钮到配置
     *
     * @param context 上下文
     * @param config 控件配置
     * @return 新创建的按钮数据
     */
    fun addButton(config: ControlConfig): ControlData.Button {

        val button = ControlData.Button()
        button.name = context.getString(R.string.editor_default_button_name)
        button.x = 0.5f
        button.y = 0.5f
        button.width = 150f
        button.height = 150f
        button.opacity = 0.5f
        button.isVisible = true
        button.keycode = KeyCode.KEYBOARD_SPACE

        config.controls.add(button)
        return button
    }

    /**
     * 添加摇杆到配置
     *
     * @param config 控件配置
     * @param joystickMode 摇杆模式
     * @param isRightStick 是否为右摇杆
     * @return 新创建的摇杆数据
     */
    fun addJoystick(
        config: ControlConfig, joystickMode: Joystick.Mode, isRightStick: Boolean
    ): Joystick {
        val joystick = Joystick()
        joystick.name = if (isRightStick) "右摇杆" else "左摇杆"
        joystick.joystickMode = joystickMode
        joystick.isRightStick = isRightStick

        // 设置位置
        if (isRightStick) {
            joystick.x = 0.75f
        } else {
            joystick.x = 0.25f
        }
        joystick.y = 0.75f

        // 设置尺寸和样式
        joystick.width = 450f
        joystick.height = 450f
        joystick.opacity = 0.5f
        joystick.bgColor = -0xb5b5b6
        joystick.strokeColor = 0x66FFFFFF
        joystick.strokeWidth = 2f
        joystick.cornerRadius = 225f
        joystick.stickOpacity = 0.8f
        joystick.stickKnobSize = 0.35f

        // 设置按键映射（仅键盘模式需要）
        if (joystickMode == Joystick.Mode.KEYBOARD) {
            joystick.joystickKeys = arrayOf(
                KeyCode.KEYBOARD_W, KeyCode.KEYBOARD_D, KeyCode.KEYBOARD_S, KeyCode.KEYBOARD_A
            )
        }

        config.controls.add(joystick)
        return joystick
    }

    /**
     * 添加触控板到配置
     *
     * @param context 上下文
     * @param config 控件配置
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @return 新创建的触控板数据
     */
    fun addTouchPad(config: ControlConfig): TouchPad {
        val touchpad = TouchPad()
        touchpad.name = context.getString(R.string.editor_default_touchpad_name)
        touchpad.x = 0.5f
        touchpad.y = 0.5f
        touchpad.width = 450f
        touchpad.height = 450f
        touchpad.opacity = 0.5f
        touchpad.cornerRadius = 22.0f
        touchpad.isVisible = true

        config.controls.add(touchpad)
        return touchpad
    }

    /**
     * 添加文本控件到配置
     *
     * @param context 上下文
     * @param config 控件配置
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @return 新创建的文本控件数据
     */
    fun addText(config: ControlConfig): ControlData.Text {
        val defaultTextName = context.getString(R.string.editor_default_text_name)
        val text = ControlData.Text()
        text.name = defaultTextName
        text.x = 0.5f
        text.y = 0.5f
        text.width = 150f
        text.height = 150f
        text.opacity = 0.5f
        text.bgColor = -0x7f7f80 // 灰色背景（更清晰可见）
        text.isVisible = true
        text.shape = ControlData.Text.Shape.RECTANGLE // 默认方形
        text.displayText = defaultTextName // 默认文本

        config.controls.add(text)
        return text
    }

    /**
     * 显示摇杆模式批量设置对话框
     *
     * @param context 上下文
     * @param config 控件配置
     * @param onLayoutUpdated 布局更新回调
     */
    fun showJoystickModeDialog(
        context: Context, config: ControlConfig?, onLayoutUpdated: Runnable?
    ) {
        if (config == null) return

        // 统计当前布局中的摇杆数量
        var joystickCount = 0
        for (control in config.controls) {
            if (control is Joystick) {
                joystickCount++
            }
        }

        if (joystickCount == 0) {
            Toast.makeText(
                context, context.getString(R.string.editor_no_joystick), Toast.LENGTH_SHORT
            ).show()
            return
        }

        val modes = arrayOf<String?>(
            context.getString(R.string.editor_joystick_mode_keyboard),
            context.getString(R.string.editor_joystick_mode_mouse),
            context.getString(R.string.editor_joystick_mode_sdl)
        )

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.editor_joystick_mode_settings))
            .setMessage(context.getString(R.string.editor_joystick_mode_message, joystickCount))
            .setItems(
                modes
            ) { dialog: DialogInterface?, which: Int ->
                val newMode: Joystick.Mode?
                val modeName: String?

                when (which) {
                    0 -> {
                        newMode = Joystick.Mode.KEYBOARD
                        modeName = context.getString(R.string.editor_mode_keyboard_detailed)
                    }

                    1 -> {
                        newMode = Joystick.Mode.MOUSE
                        modeName = context.getString(R.string.editor_mode_mouse_detailed)
                    }

                    2 -> {
                        newMode = Joystick.Mode.GAMEPAD
                        modeName = context.getString(R.string.editor_mode_xbox_detailed)
                    }

                    else -> return@setItems
                }

                // 批量更新所有摇杆的模式
                val updatedCount = updateJoystickModes(config, newMode)

                // 通知布局已更新
                onLayoutUpdated?.run()
                Toast.makeText(
                    context,
                    context.getString(R.string.editor_joysticks_set, updatedCount, modeName),
                    Toast.LENGTH_SHORT
                ).show()
            }.setNegativeButton(context.getString(R.string.cancel), null).show()
    }

    /**
     * 批量更新摇杆模式
     *
     * @param context 上下文
     * @param config 控件配置
     * @param newMode 新模式
     * @return 更新的摇杆数量
     */
    private fun updateJoystickModes(config: ControlConfig, newMode: Joystick.Mode): Int {
        var updatedCount = 0

        config.controls.mapNotNull { it as? Joystick }.forEach { joystick ->
            joystick.joystickMode = newMode
            updatedCount++
        }

        return updatedCount
    }

    /**
     * 保存布局到 ControlConfigManager
     *
     * @param context 上下文
     * @param config 控件配置
     * @return 是否保存成功
     */
    fun saveLayout(context: Context, config: ControlConfig?): Boolean {
        if (config == null) {
            Toast.makeText(
                context, context.getString(R.string.editor_no_layout_to_save), Toast.LENGTH_SHORT
            ).show()
            return false
        }

        try {
            val manager = RaLaunchApplication.getControlConfigManager()
            manager.saveConfig(config)

            Toast.makeText(
                context, context.getString(R.string.editor_layout_saved), Toast.LENGTH_SHORT
            ).show()
            return true
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.editor_save_failed, e.message),
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
    }

    /**
     * 从 ControlConfigManager 加载布局
     *
     * @param layoutId 布局ID（null 表示使用当前选中的布局）
     * @return 加载的配置，如果失败返回 null
     */
    fun loadLayout(layoutId: String?): ControlConfig? {
        try {
            val manager = RaLaunchApplication.getControlConfigManager()

            if (layoutId != null) {
                return manager.loadConfig(layoutId)
            } else {
                val selectedId = manager.getSelectedConfigId()
                if (selectedId != null) {
                    return manager.loadConfig(selectedId)
                }
            }

            return null
        } catch (_: Exception) {
            return null
        }
    }
}

