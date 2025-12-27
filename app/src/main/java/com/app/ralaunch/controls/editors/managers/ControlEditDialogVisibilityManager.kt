package com.app.ralaunch.controls.editors.managers

import android.view.View
import com.app.ralaunch.controls.configs.ControlData

/**
 * 控件编辑对话框可见性管理器
 * 统一管理各个选项的可见性逻辑（根据控件类型、形状等动态显示/隐藏选项）
 */
object ControlEditDialogVisibilityManager {

    /**
     * 更新外观选项的可见性（根据控件类型和形状）
     */
    fun updateAppearanceOptionsVisibility(view: View, data: ControlData) {
        val cardCornerRadius = view.findViewById<View?>(com.app.ralaunch.R.id.card_corner_radius)
        val cardStickOpacity = view.findViewById<View?>(com.app.ralaunch.R.id.card_stick_opacity)
        val cardStickKnobSize = view.findViewById<View?>(com.app.ralaunch.R.id.card_stick_knob_size)

        // 圆角半径（仅在矩形形状时显示）
        val isRectangle = when (data) {
            is ControlData.Button -> data.shape == ControlData.Button.Shape.RECTANGLE
            is ControlData.Text -> data.shape == ControlData.Text.Shape.RECTANGLE
            else -> false
        }
        val isJoystick = data is ControlData.Joystick
        cardCornerRadius?.visibility = if (isRectangle) View.VISIBLE else View.GONE

        // 摇杆中心透明度（仅摇杆类型显示）
        cardStickOpacity?.visibility = if (isJoystick) View.VISIBLE else View.GONE

        // 摇杆圆心大小（仅摇杆类型显示）
        cardStickKnobSize?.visibility = if (isJoystick) View.VISIBLE else View.GONE
    }

    /**
     * 更新基本信息选项的可见性（根据控件类型）
     */
    fun updateBasicInfoOptionsVisibility(view: View, data: ControlData) {
        val itemControlShape = view.findViewById<View?>(com.app.ralaunch.R.id.item_control_shape)
        val itemJoystickMode = view.findViewById<View?>(com.app.ralaunch.R.id.item_joystick_mode)
        val itemTextContent = view.findViewById<View?>(com.app.ralaunch.R.id.item_text_content)

        // 形状选择（仅按钮和文本控件显示）
        val isButton = data is ControlData.Button
        val isText = data is ControlData.Text
        itemControlShape?.visibility = if (isButton || isText) View.VISIBLE else View.GONE

        // 摇杆模式选择（仅摇杆类型显示）
        val isJoystick = data is ControlData.Joystick
        itemJoystickMode?.visibility = if (isJoystick) View.VISIBLE else View.GONE

        // 文本内容编辑（仅文本控件显示）
        itemTextContent?.visibility = if (isText) View.VISIBLE else View.GONE
    }

    /**
     * 更新位置大小选项的可见性（根据控件类型）
     */
    fun updatePositionSizeOptionsVisibility(view: View, data: ControlData) {
        // 摇杆大小显示由bindPositionSizeViews控制，这里不需要特殊处理
        // 如果有item_joystick_size资源，可以在这里设置可见性
    }

    /**
     * 更新键值设置选项的可见性（根据控件类型）
     */
    fun updateKeymapOptionsVisibility(view: View, data: ControlData) {
        val itemKeyMapping = view.findViewById<View?>(com.app.ralaunch.R.id.item_key_mapping)
        val itemJoystickKeyMapping =
            view.findViewById<View?>(com.app.ralaunch.R.id.item_joystick_key_mapping)
        val itemToggleMode = view.findViewById<View?>(com.app.ralaunch.R.id.item_toggle_mode)

        // 按钮和文本控件显示普通键值设置
        val isButton = data is ControlData.Button
        // TODO: 当前文本控件不支持键值设置，如需支持，取消注释下一行
        val isText = data is ControlData.Text
        val isJoystick = data is ControlData.Joystick

        // 普通键值设置（仅按钮和文本控件显示）
        itemKeyMapping?.visibility = if (isButton || isText) View.VISIBLE else View.GONE

        // 摇杆键值设置（仅摇杆显示，且为键盘模式）
        val showJoystickKeyMapping = if (isJoystick) {
            (data as ControlData.Joystick).mode == ControlData.Joystick.Mode.KEYBOARD
        } else false

        itemJoystickKeyMapping?.visibility =
            if (showJoystickKeyMapping) View.VISIBLE else View.GONE

        // 切换模式（仅按钮显示）
        itemToggleMode?.visibility = if (isButton) View.VISIBLE else View.GONE
    }

    /**
     * 更新文本选项的可见性（根据控件类型）
     */
    fun updateTextOptionsVisibility(view: View, data: ControlData) {
        val cardTextOpacity = view.findViewById<View?>(com.app.ralaunch.R.id.card_text_opacity)

        // 文本透明度（仅文本控件显示，按钮也可显示）
        val isText = data is ControlData.Text
        cardTextOpacity?.visibility = if (isText) View.VISIBLE else View.GONE
    }

    /**
     * 更新鼠标模式选项的可见性（仅摇杆 + 鼠标模式时显示）
     */
    fun updateMouseModeOptionsVisibility(view: View, data: ControlData) {
        // 如果有鼠标相关的资源ID，可以在这里处理
        // val cardMouseRange = view.findViewById<View?>(com.app.ralaunch.R.id.card_mouse_range)
        // val cardMouseSpeed = view.findViewById<View?>(com.app.ralaunch.R.id.card_mouse_speed)

        val isMouseMode = if (data is ControlData.Joystick) {
            data.mode == ControlData.Joystick.Mode.MOUSE
        } else false

        // cardMouseRange?.visibility = if (isMouseMode) View.VISIBLE else View.GONE
        // cardMouseSpeed?.visibility = if (isMouseMode) View.VISIBLE else View.GONE
    }

    /**
     * 更新手柄摇杆选择选项的可见性（仅摇杆 + 手柄模式时显示）
     */
    fun updateJoystickStickSelectOptionsVisibility(view: View, data: ControlData) {
        val itemJoystickStickSelect =
            view.findViewById<View?>(com.app.ralaunch.R.id.item_joystick_stick_select)

        val showStickSelect = if (data is ControlData.Joystick) {
            data.mode == ControlData.Joystick.Mode.GAMEPAD ||
            data.mode == ControlData.Joystick.Mode.MOUSE
        } else false

        // 摇杆左右选择（仅在手柄模式或鼠标模式时显示）
        itemJoystickStickSelect?.visibility = if (showStickSelect) View.VISIBLE else View.GONE
    }

    /**
     * 更新手柄摇杆选择显示文本
     */
    fun updateJoystickStickSelectDisplayText(view: View, data: ControlData) {
        val tvJoystickStickSelect =
            view.findViewById<android.widget.TextView?>(com.app.ralaunch.R.id.tv_joystick_stick_select)

        if (tvJoystickStickSelect != null && data is ControlData.Joystick) {
            val context = view.context
            val text = if (data.isRightStick) {
                context.getString(com.app.ralaunch.R.string.editor_right_stick)
            } else {
                context.getString(com.app.ralaunch.R.string.editor_left_stick)
            }
            tvJoystickStickSelect.text = text
        }
    }

    /**
     * 更新所有选项的可见性
     * （用于初始化或切换控件类型时）
     */
    fun updateAllOptionsVisibility(view: View, data: ControlData) {
        updateBasicInfoOptionsVisibility(view, data)
        updatePositionSizeOptionsVisibility(view, data)
        updateAppearanceOptionsVisibility(view, data)
        updateKeymapOptionsVisibility(view, data)
        updateTextOptionsVisibility(view, data)
        updateMouseModeOptionsVisibility(view, data)
        updateJoystickStickSelectOptionsVisibility(view, data)
    }

    /**
     * 更新所有选项的可见性（包括显示文本）
     */
    fun updateAllOptionsVisibility(
        view: View,
        data: ControlData,
        updateDisplayText: Boolean
    ) {
        updateAllOptionsVisibility(view, data)

        if (updateDisplayText) {
            updateJoystickStickSelectDisplayText(view, data)
        }
    }

    /**
     * 根据规则设置View的可见性
     */
    fun setVisibilityByRule(
        view: View?,
        viewId: Int,
        data: ControlData?,
        rule: VisibilityRule
    ) {
        val targetView = view?.findViewById<View?>(viewId)
        if (targetView != null) {
            targetView.visibility = if (rule.shouldShow(data)) View.VISIBLE else View.GONE
        }
    }

    /**
     * 可见性规则接口
     */
    interface VisibilityRule {
        fun shouldShow(data: ControlData?): Boolean
    }

    /**
     * 创建类型规则（检查是否为指定类型）
     */
    fun createTypeRule(targetType: Class<out ControlData>): VisibilityRule {
        return object : VisibilityRule {
            override fun shouldShow(data: ControlData?): Boolean {
                return data != null && targetType.isInstance(data)
            }
        }
    }

    /**
     * 创建形状规则（检查是否为指定形状）
     */
    fun createShapeRule(targetShape: Any): VisibilityRule {
        return object : VisibilityRule {
            override fun shouldShow(data: ControlData?): Boolean {
                return when (data) {
                    is ControlData.Button -> data.shape == targetShape
                    is ControlData.Text -> data.shape == targetShape
                    else -> false
                }
            }
        }
    }

    /**
     * 创建AND规则（多个规则同时满足）
     */
    fun createAndRule(vararg rules: VisibilityRule): VisibilityRule {
        return object : VisibilityRule {
            override fun shouldShow(data: ControlData?): Boolean {
                for (rule in rules) {
                    if (!rule.shouldShow(data)) {
                        return false
                    }
                }
                return true
            }
        }
    }

    /**
     * 创建OR规则（多个规则满足其一即可）
     */
    fun createOrRule(vararg rules: VisibilityRule): VisibilityRule {
        return object : VisibilityRule {
            override fun shouldShow(data: ControlData?): Boolean {
                for (rule in rules) {
                    if (rule.shouldShow(data)) {
                        return true
                    }
                }
                return false
            }
        }
    }

    /**
     * 创建NOT规则（反转规则）
     */
    fun createNotRule(rule: VisibilityRule): VisibilityRule {
        return object : VisibilityRule {
            override fun shouldShow(data: ControlData?): Boolean {
                return !rule.shouldShow(data)
            }
        }
    }
}

