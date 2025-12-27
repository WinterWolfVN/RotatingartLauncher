package com.app.ralaunch.controls.editors

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import com.app.ralaunch.R
import com.app.ralaunch.controls.configs.ControlData
import com.app.ralaunch.utils.LocalizedDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * MD3风格的键值选择对话框
 * 支持在对话框内切换键盘按键和手柄按键模式
 */
class KeySelectorDialog
@JvmOverloads constructor(context: Context, private var isGamepadMode: Boolean = false) :
    LocalizedDialog(context) {
    private var listener: OnKeySelectedListener? = null
    private var contentContainer: LinearLayout? = null
    private var toggleGroup: MaterialButtonToggleGroup? = null
    private var btnKeyboardMode: MaterialButton? = null
    private var btnGamepadMode: MaterialButton? = null

    interface OnKeySelectedListener {
        fun onKeySelected(keycode: Int, keyName: String?)
    }

    fun setOnKeySelectedListener(listener: OnKeySelectedListener?) {
        this.listener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置无标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val localizedContext = getLocalizedContext()

        // 创建主容器
        val mainLayout = LinearLayout(context)
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))

        // 创建模式切换组
        toggleGroup = MaterialButtonToggleGroup(context)
        toggleGroup!!.isSingleSelection = true
        toggleGroup!!.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        btnKeyboardMode = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btnKeyboardMode!!.id = View.generateViewId()
        btnKeyboardMode!!.text = "Keyboard"
        btnKeyboardMode!!.layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )

        btnGamepadMode = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btnGamepadMode!!.id = View.generateViewId()
        btnGamepadMode!!.text = "Gamepad"
        btnGamepadMode!!.layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )

        toggleGroup!!.addView(btnKeyboardMode)
        toggleGroup!!.addView(btnGamepadMode)
        mainLayout.addView(toggleGroup)

        // 创建内容容器（可滚动）
        val scrollView = ScrollView(context)
        scrollView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )

        contentContainer = LinearLayout(context)
        contentContainer!!.orientation = LinearLayout.VERTICAL
        scrollView.addView(contentContainer)
        mainLayout.addView(scrollView)

        setContentView(mainLayout)

        // 设置初始选中状态
        if (isGamepadMode) {
            btnGamepadMode?.isChecked = true
        } else {
            btnKeyboardMode?.isChecked = true
        }

        // 初始化按键布局
        updateKeyLayout()

        // 监听模式切换
        toggleGroup?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isGamepadMode = checkedId == btnGamepadMode!!.id
                updateKeyLayout()
            }
        }

        // 设置对话框宽高
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.8).toInt()
        )
    }

    /**
     * 添加键盘按键布局
     */
    private fun addKeyboardKeys(layout: LinearLayout) {
        // 功能键行
        val row1 = createKeyRow()
        addKeyToRow(row1, "Esc", ControlData.KeyCode.KEYBOARD_ESCAPE)
        layout.addView(row1)

        // 数字键行
        val row2 = createKeyRow()
        for (i in 1..9) {
            val keyCode = ControlData.KeyCode.entries.find { it.name == "KEYBOARD_$i" }
            if (keyCode != null) {
                val keyView = createKeyButton(i.toString())
                keyView.setOnClickListener {
                    listener?.onKeySelected(keyCode.code, i.toString())
                    dismiss()
                }
                row2.addView(keyView)
            }
        }
        layout.addView(row2)

        // QWERTY行
        val row3 = createKeyRow()
        addKeyToRow(row3, "W", ControlData.KeyCode.KEYBOARD_W)
        addKeyToRow(row3, "E", ControlData.KeyCode.KEYBOARD_E)
        layout.addView(row3)

        // ASDF行
        val row4 = createKeyRow()
        addKeyToRow(row4, "A", ControlData.KeyCode.KEYBOARD_A)
        addKeyToRow(row4, "S", ControlData.KeyCode.KEYBOARD_S)
        addKeyToRow(row4, "D", ControlData.KeyCode.KEYBOARD_D)
        addKeyToRow(row4, "H", ControlData.KeyCode.KEYBOARD_H)
        layout.addView(row4)

        // 底部功能键
        val row5 = createKeyRow()
        addKeyToRow(row5, "Enter", ControlData.KeyCode.KEYBOARD_RETURN)
        layout.addView(row5)

        // Shift键行
        val row6 = createKeyRow()
        addKeyToRow(row6, "Shift", ControlData.KeyCode.KEYBOARD_LSHIFT)
        addKeyToRow(row6, "Shift", ControlData.KeyCode.KEYBOARD_RSHIFT)
        layout.addView(row6)

        // 控制键行
        val row7 = createKeyRow()
        addKeyToRow(row7, "Ctrl", ControlData.KeyCode.KEYBOARD_LCTRL)
        addKeyToRow(row7, "Space", ControlData.KeyCode.KEYBOARD_SPACE)
        addKeyToRow(row7, "Ctrl", ControlData.KeyCode.KEYBOARD_RCTRL)
        layout.addView(row7)

        // 鼠标按键行
        val row8 = createKeyRow()
        addKeyToRow(row8, "LMB", ControlData.KeyCode.MOUSE_LEFT)
        addKeyToRow(row8, "RMB", ControlData.KeyCode.MOUSE_RIGHT)
        addKeyToRow(row8, "MMB", ControlData.KeyCode.MOUSE_MIDDLE)
        addKeyToRow(row8, "MW↑", ControlData.KeyCode.MOUSE_WHEEL_UP)
        addKeyToRow(row8, "MW↓", ControlData.KeyCode.MOUSE_WHEEL_DOWN)
        layout.addView(row8)

        // 特殊功能键
        val row9 = createKeyRow()
        addKeyToRow(row9, "Keyboard", ControlData.KeyCode.SPECIAL_KEYBOARD)
        layout.addView(row9)
    }

    /**
     * 添加手柄按键布局
     */
    private fun addGamepadKeys(layout: LinearLayout) {

        // ABXY按键行
        val row1 = createKeyRow()
        addKeyToRow(row1, "A", ControlData.KeyCode.XBOX_BUTTON_A)
        addKeyToRow(row1, "B", ControlData.KeyCode.XBOX_BUTTON_B)
        addKeyToRow(row1, "X", ControlData.KeyCode.XBOX_BUTTON_X)
        addKeyToRow(row1, "Y", ControlData.KeyCode.XBOX_BUTTON_Y)
        layout.addView(row1)

        // 肩键行
        val row2 = createKeyRow()
        addKeyToRow(row2, "LB", ControlData.KeyCode.XBOX_BUTTON_LB)
        addKeyToRow(row2, "RB", ControlData.KeyCode.XBOX_BUTTON_RB)
        addKeyToRow(row2, "LT", ControlData.KeyCode.XBOX_TRIGGER_LEFT)
        addKeyToRow(row2, "RT", ControlData.KeyCode.XBOX_TRIGGER_RIGHT)
        layout.addView(row2)

        // 摇杆按键行
        val row3 = createKeyRow()
        addKeyToRow(row3, "L3", ControlData.KeyCode.XBOX_BUTTON_LEFT_STICK)
        addKeyToRow(row3, "R3", ControlData.KeyCode.XBOX_BUTTON_RIGHT_STICK)
        layout.addView(row3)

        // 方向键行
        val row4 = createKeyRow()
        addKeyToRow(row4, "D-Pad ↑", ControlData.KeyCode.XBOX_BUTTON_DPAD_UP)
        addKeyToRow(row4, "D-Pad ↓", ControlData.KeyCode.XBOX_BUTTON_DPAD_DOWN)
        addKeyToRow(row4, "D-Pad ←", ControlData.KeyCode.XBOX_BUTTON_DPAD_LEFT)
        addKeyToRow(row4, "D-Pad →", ControlData.KeyCode.XBOX_BUTTON_DPAD_RIGHT)
        layout.addView(row4)

        // 功能键行
        val row5 = createKeyRow()
        addKeyToRow(row5, "Start", ControlData.KeyCode.XBOX_BUTTON_START)
        addKeyToRow(row5, "Back", ControlData.KeyCode.XBOX_BUTTON_BACK)
        addKeyToRow(row5, "Guide", ControlData.KeyCode.XBOX_BUTTON_GUIDE)
        layout.addView(row5)
    }

    /**
     * 更新按键布局（切换键盘/手柄模式时调用）
     */
    private fun updateKeyLayout() {
        contentContainer?.let {
            it.removeAllViews()
            if (isGamepadMode) {
                addGamepadKeys(it)
            } else {
                addKeyboardKeys(it)
            }
        }
    }

    /**
     * 创建按键行容器
     */
    private fun createKeyRow(): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        row.setPadding(0, dpToPx(4), 0, dpToPx(4))
        return row
    }

    /**
     * 创建按键按钮
     */
    private fun createKeyButton(text: String): MaterialButton {
        val button = MaterialButton(context)
        button.text = text
        button.layoutParams = LinearLayout.LayoutParams(
            dpToPx(60),
            dpToPx(48)
        ).apply {
            setMargins(dpToPx(4), 0, dpToPx(4), 0)
        }
        return button
    }

    /**
     * 添加按键到行
     */
    private fun addKeyToRow(row: LinearLayout, label: String, keyCode: ControlData.KeyCode) {
        val button = createKeyButton(label)
        button.setOnClickListener {
            listener?.onKeySelected(keyCode.code, label)
            dismiss()
        }
        row.addView(button)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}

