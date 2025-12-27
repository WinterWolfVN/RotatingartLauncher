package com.app.ralaunch.controls.editors.managers

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.app.ralaunch.R
import com.app.ralaunch.controls.configs.ControlData
import com.app.ralaunch.controls.editors.ControlEditDialogMD
import com.app.ralaunch.controls.editors.managers.ControlColorManager.updateColorView
import com.app.ralaunch.controls.editors.managers.ControlEditDialogSeekBarManager.UpdateNotifier
import com.app.ralaunch.controls.editors.managers.ControlEditDialogSeekBarManager.ValueSetter
import com.app.ralaunch.controls.editors.managers.ControlJoystickModeManager.OnModeSelectedListener
import com.app.ralaunch.controls.editors.managers.ControlShapeManager.OnShapeSelectedListener
import com.app.ralaunch.controls.editors.managers.ControlTypeManager.OnTypeSelectedListener
import com.app.ralaunch.data.SettingsManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import kotlin.math.max
import kotlin.math.min

/**
 * 控件编辑对话框UI绑定管理器
 * 统一管理所有UI元素的绑定和事件监听器设置
 */
object ControlEditDialogUIBinder {
    /**
     * 绑定基本信息视图
     */
    fun bindBasicInfoViews(
        view: View,
        refs: UIReferences,
        dialog: ControlEditDialogMD
    ) {
        val itemControlType = view.findViewById<MaterialCardView?>(R.id.item_control_type)
        val tvControlType = view.findViewById<TextView?>(R.id.tv_control_type)
        val itemControlShape = view.findViewById<MaterialCardView?>(R.id.item_control_shape)
        val tvControlShape = view.findViewById<TextView?>(R.id.tv_control_shape)
        val etName = view.findViewById<EditText?>(R.id.et_control_name)

        if (itemControlType != null) {
            itemControlType.setOnClickListener {
                ControlTypeManager.showTypeSelectDialog(
                    dialog.requireContext(),
                    refs.currentData,
                    object : OnTypeSelectedListener {
                        override fun onTypeSelected(data: ControlData?) {
                            ControlTypeManager.updateTypeDisplay(
                                dialog.requireContext(),
                                data,
                                tvControlType
                            )
                            refs.notifyUpdate()
                        }
                    })
            }
        }

        if (itemControlShape != null) {
            itemControlShape.setOnClickListener {
                ControlShapeManager.showShapeSelectDialog(
                    dialog.requireContext(),
                    refs.currentData,
                    object : OnShapeSelectedListener {
                        override fun onShapeSelected(data: ControlData?) {
                            ControlShapeManager.updateShapeDisplay(
                                dialog.requireContext(),
                                data,
                                tvControlShape,
                                itemControlShape
                            )
                            refs.notifyUpdate()
                        }
                    })
            }
        }


        // 摇杆模式选择（仅摇杆类型显示）
        val itemJoystickMode = view.findViewById<MaterialCardView?>(R.id.item_joystick_mode)
        val tvJoystickMode = view.findViewById<TextView?>(R.id.tv_joystick_mode)
        if (itemJoystickMode != null) {
            itemJoystickMode.setOnClickListener {
                ControlJoystickModeManager.showModeSelectDialog(
                    dialog.requireContext(),
                    refs.currentData,
                    object : OnModeSelectedListener {
                        override fun onModeSelected(data: ControlData?) {
                            ControlJoystickModeManager.updateModeDisplay(
                                dialog.requireContext(),
                                data,
                                tvJoystickMode
                            )
                            // 模式改变时，更新摇杆左右选择的可见性
                            ControlEditDialogVisibilityManager.updateBasicInfoOptionsVisibility(
                                view,
                                data!!
                            )
                            refs.notifyUpdate()
                        }
                    })
            }
        }

        if (etName != null) {
            etName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    // 切换控件时不修改数据，避免把旧文本写入新控件
                    if (refs.currentData != null && !refs.isUpdating) {
                        refs.currentData!!.name = s.toString()
                        refs.notifyUpdate()
                    }
                }

                override fun afterTextChanged(s: Editable?) {}
            })
        }


        // 文本内容编辑（仅文本控件显示）
        val etTextContent = view.findViewById<EditText?>(R.id.et_text_content)
        if (etTextContent != null) {
            etTextContent.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    // 切换控件时不修改数据，避免把旧文本写入新控件
                    val data = refs.currentData
                    if (data is ControlData.Text && !refs.isUpdating) {
                        data.displayText = s.toString()
                        refs.notifyUpdate()
                    }
                }

                override fun afterTextChanged(s: Editable?) {}
            })
        }


        // 摇杆左右选择开关（仅摇杆类型且为手柄模式或鼠标模式时显示）
        val switchJoystickStickSelect =
            view.findViewById<SwitchCompat?>(R.id.switch_joystick_stick_select)
        val tvJoystickStickSelect = view.findViewById<TextView?>(R.id.tv_joystick_stick_select)
        if (switchJoystickStickSelect != null) {
            switchJoystickStickSelect.setOnCheckedChangeListener { _, isChecked ->
                val data = refs.currentData
                if (data is ControlData.Joystick) {
                    data.isRightStick = isChecked
                    // 更新显示文本
                    if (tvJoystickStickSelect != null) {
                        val context = refs.context
                        tvJoystickStickSelect.setText(
                            if (isChecked) context.getString(R.string.editor_right_stick) else context.getString(
                                R.string.editor_left_stick
                            )
                        )
                    }
                    // 更新攻击模式选项的可见性
                    ControlEditDialogVisibilityManager.updateBasicInfoOptionsVisibility(
                        view,
                        refs.currentData!!
                    )
                    refs.notifyUpdate()
                }
            }
        }


        // 右摇杆攻击模式 RadioGroup（仅摇杆类型且为鼠标模式且为右摇杆时显示）
        val rgAttackMode = view.findViewById<RadioGroup?>(R.id.rg_attack_mode)
        if (rgAttackMode != null) {
            // 从全局设置读取攻击模式
            val settingsManager = SettingsManager.getInstance(dialog.getContext())
            val attackMode = settingsManager.getMouseRightStickAttackMode()


            // 初始化选中状态
            when (attackMode) {
                SettingsManager.ATTACK_MODE_HOLD -> rgAttackMode.check(R.id.rb_attack_mode_hold)
                SettingsManager.ATTACK_MODE_CLICK -> rgAttackMode.check(R.id.rb_attack_mode_click)
                SettingsManager.ATTACK_MODE_CONTINUOUS -> rgAttackMode.check(R.id.rb_attack_mode_continuous)
            }


            // 监听选择变化，保存到全局设置
            rgAttackMode.setOnCheckedChangeListener { group: RadioGroup?, checkedId: Int ->
                var mode = SettingsManager.ATTACK_MODE_HOLD
                if (checkedId == R.id.rb_attack_mode_hold) {
                    mode = SettingsManager.ATTACK_MODE_HOLD
                } else if (checkedId == R.id.rb_attack_mode_click) {
                    mode = SettingsManager.ATTACK_MODE_CLICK
                } else if (checkedId == R.id.rb_attack_mode_continuous) {
                    mode = SettingsManager.ATTACK_MODE_CONTINUOUS
                }
                settingsManager.setMouseRightStickAttackMode(mode)
                refs.notifyUpdate()
            }
        }


        // 触摸穿透开关
        val switchPassThrough = view.findViewById<SwitchCompat?>(R.id.switch_pass_through)
        if (switchPassThrough != null) {
            switchPassThrough.setOnCheckedChangeListener { _, isChecked ->
                // TODO: Add passThrough field to TouchPad class if needed
                // val data = refs.currentData
                // if (data is ControlData.TouchPad) {
                //     data.passThrough = isChecked
                //     refs.notifyUpdate()
                // }
            }
        }


        // 鼠标移动范围设置（使用全局设置）
        val rangeSettingsManager = SettingsManager.getInstance(dialog.getContext())

        val sliderMouseRangeLeft = view.findViewById<Slider?>(R.id.seekbar_mouse_range_left)
        val tvMouseRangeLeft = view.findViewById<TextView?>(R.id.tv_mouse_range_left)
        val sliderMouseRangeTop = view.findViewById<Slider?>(R.id.seekbar_mouse_range_top)
        val tvMouseRangeTop = view.findViewById<TextView?>(R.id.tv_mouse_range_top)
        val sliderMouseRangeRight = view.findViewById<Slider?>(R.id.seekbar_mouse_range_right)
        val tvMouseRangeRight = view.findViewById<TextView?>(R.id.tv_mouse_range_right)
        val sliderMouseRangeBottom = view.findViewById<Slider?>(R.id.seekbar_mouse_range_bottom)
        val tvMouseRangeBottom = view.findViewById<TextView?>(R.id.tv_mouse_range_bottom)


        // 初始化鼠标范围值（从全局设置读取）
        if (sliderMouseRangeLeft != null) {
            val leftProgress = (rangeSettingsManager.getMouseRightStickRangeLeft() * 100).toInt()
            sliderMouseRangeLeft.setValue(leftProgress.toFloat())
            if (tvMouseRangeLeft != null) tvMouseRangeLeft.setText(leftProgress.toString() + "%")

            sliderMouseRangeLeft.addOnChangeListener { slider: Slider?, value: Float, fromUser: Boolean ->
                val progress = value.toInt()
                if (tvMouseRangeLeft != null) tvMouseRangeLeft.setText(progress.toString() + "%")
                if (fromUser) {
                    val rangeValue = progress / 100f
                    rangeSettingsManager.setMouseRightStickRangeLeft(rangeValue)
                    Log.i(
                        "ControlEditDialog",
                        "Saved mouse range LEFT: " + progress + "% -> " + rangeValue
                    )
                    refs.notifyUpdate()
                }
            }
        }

        if (sliderMouseRangeTop != null) {
            val topProgress = (rangeSettingsManager.getMouseRightStickRangeTop() * 100).toInt()
            sliderMouseRangeTop.setValue(topProgress.toFloat())
            if (tvMouseRangeTop != null) tvMouseRangeTop.setText(topProgress.toString() + "%")

            sliderMouseRangeTop.addOnChangeListener { slider: Slider?, value: Float, fromUser: Boolean ->
                val progress = value.toInt()
                if (tvMouseRangeTop != null) tvMouseRangeTop.setText(progress.toString() + "%")
                if (fromUser) {
                    rangeSettingsManager.setMouseRightStickRangeTop(progress / 100f)
                    refs.notifyUpdate()
                }
            }
        }

        if (sliderMouseRangeRight != null) {
            val rightProgress = (rangeSettingsManager.getMouseRightStickRangeRight() * 100).toInt()
            sliderMouseRangeRight.setValue(rightProgress.toFloat())
            if (tvMouseRangeRight != null) tvMouseRangeRight.setText(rightProgress.toString() + "%")

            sliderMouseRangeRight.addOnChangeListener { slider: Slider?, value: Float, fromUser: Boolean ->
                val progress = value.toInt()
                if (tvMouseRangeRight != null) tvMouseRangeRight.setText(progress.toString() + "%")
                if (fromUser) {
                    rangeSettingsManager.setMouseRightStickRangeRight(progress / 100f)
                    refs.notifyUpdate()
                }
            }
        }

        if (sliderMouseRangeBottom != null) {
            val bottomProgress =
                (rangeSettingsManager.getMouseRightStickRangeBottom() * 100).toInt()
            sliderMouseRangeBottom.setValue(bottomProgress.toFloat())
            if (tvMouseRangeBottom != null) tvMouseRangeBottom.setText(bottomProgress.toString() + "%")

            sliderMouseRangeBottom.addOnChangeListener { slider: Slider?, value: Float, fromUser: Boolean ->
                val progress = value.toInt()
                if (tvMouseRangeBottom != null) tvMouseRangeBottom.setText(progress.toString() + "%")
                if (fromUser) {
                    rangeSettingsManager.setMouseRightStickRangeBottom(progress / 100f)
                    refs.notifyUpdate()
                }
            }
        }


        // 鼠标移动速度滑块（使用全局设置，范围60-200，步进10）
        val sliderMouseSpeed = view.findViewById<Slider?>(R.id.seekbar_mouse_speed)
        val tvMouseSpeed = view.findViewById<TextView?>(R.id.tv_mouse_speed)
        if (sliderMouseSpeed != null) {
            val speedProgress = rangeSettingsManager.getMouseRightStickSpeed()
            // 对齐到步进值（stepSize=10, valueFrom=60）
            val stepSize = 10.0f
            val valueFrom = 60.0f
            var alignedValue =
                valueFrom + Math.round((speedProgress - valueFrom) / stepSize) * stepSize
            // 确保在范围内
            alignedValue = max(valueFrom, min(200.0f, alignedValue))
            sliderMouseSpeed.setValue(alignedValue)
            val displayValue = alignedValue.toInt()
            if (tvMouseSpeed != null) tvMouseSpeed.setText(displayValue.toString())

            sliderMouseSpeed.addOnChangeListener { slider: Slider?, value: Float, fromUser: Boolean ->
                val progress = value.toInt()
                if (tvMouseSpeed != null) tvMouseSpeed.setText(progress.toString())
                if (fromUser) {
                    rangeSettingsManager.setMouseRightStickSpeed(progress)
                    refs.notifyUpdate()
                }
            }
        }
    }

    /**
     * 绑定位置大小视图
     */
    fun bindPositionSizeViews(view: View, refs: UIReferences) {
        // 卡片视图
        val cardJoystickSize = view.findViewById<View?>(R.id.card_joystick_size)
        val cardWidth = view.findViewById<View?>(R.id.card_width)
        val cardHeight = view.findViewById<View?>(R.id.card_height)


        // 摇杆大小
        val sliderJoystickSize = view.findViewById<Slider?>(R.id.seekbar_joystick_size)
        val tvJoystickSizeValue = view.findViewById<TextView?>(R.id.tv_joystick_size_value)


        // 位置和尺寸
        val sliderPosX = view.findViewById<Slider?>(R.id.seekbar_pos_x)
        val tvPosXValue = view.findViewById<TextView?>(R.id.tv_pos_x_value)
        val sliderPosY = view.findViewById<Slider?>(R.id.seekbar_pos_y)
        val tvPosYValue = view.findViewById<TextView?>(R.id.tv_pos_y_value)
        val sliderWidth = view.findViewById<Slider?>(R.id.seekbar_width)
        val tvWidthValue = view.findViewById<TextView?>(R.id.tv_width_value)
        val sliderHeight = view.findViewById<Slider?>(R.id.seekbar_height)
        val tvHeightValue = view.findViewById<TextView?>(R.id.tv_height_value)
        val switchAutoSize = view.findViewById<SwitchCompat?>(R.id.switch_auto_size)


        // 根据控件类型显示/隐藏相应的卡片
        val isJoystick = refs.currentData is ControlData.Joystick

        if (cardJoystickSize != null) {
            cardJoystickSize.setVisibility(if (isJoystick) View.VISIBLE else View.GONE)
        }
        if (cardWidth != null) {
            cardWidth.setVisibility(if (isJoystick) View.GONE else View.VISIBLE)
        }
        if (cardHeight != null) {
            cardHeight.setVisibility(if (isJoystick) View.GONE else View.VISIBLE)
        }


        // 摇杆大小设置（仅摇杆显示，同时设置宽度和高度）
        if (sliderJoystickSize != null && isJoystick) {
            sliderJoystickSize.addOnChangeListener { slider: Slider?, value: Float, fromUser: Boolean ->
                val progress = value.toInt()
                if (tvJoystickSizeValue != null) tvJoystickSizeValue.setText(progress.toString() + "%")
                if (refs.currentData != null && fromUser) {
                    val size = progress / 100f
                    refs.currentData!!.width = size
                    refs.currentData!!.height = size
                    refs.notifyUpdate()
                }
            }
        }

        if (sliderPosX != null) {
            sliderPosX.addOnChangeListener { slider: Slider?, value: Float, fromUser: Boolean ->
                val progress = value.toInt()
                if (tvPosXValue != null) tvPosXValue.setText(progress.toString() + "%")
                if (refs.currentData != null && fromUser) {
                    refs.currentData!!.x = progress / 100f
                    refs.notifyUpdate()
                }
            }
        }

        if (sliderPosY != null) {
            sliderPosY.addOnChangeListener { slider: Slider?, value: Float, fromUser: Boolean ->
                val progress = value.toInt()
                if (tvPosYValue != null) tvPosYValue.setText(progress.toString() + "%")
                if (refs.currentData != null && fromUser) {
                    refs.currentData!!.y = progress / 100f
                    refs.notifyUpdate()
                }
            }
        }

        if (sliderWidth != null) {
            sliderWidth.addOnChangeListener { slider: Slider?, value: Float, fromUser: Boolean ->
                val progress = value.toInt()
                if (tvWidthValue != null) tvWidthValue.setText(progress.toString() + "%")
                if (refs.currentData != null && fromUser) {
                    val width = progress / 100f
                    refs.currentData!!.width = width
                    if (refs.currentData!!.isSizeRatioLocked) {
                        refs.currentData!!.height = width
                        // Since width and height are both relative to screen height,
                        // when autoSize is enabled, they should have the same percentage value
                        if (sliderHeight != null) {
                            sliderHeight.setValue(progress.toFloat())
                        }
                    }
                    refs.notifyUpdate()
                }
            }
        }

        if (sliderHeight != null) {
            sliderHeight.addOnChangeListener { slider: Slider?, value: Float, fromUser: Boolean ->
                val progress = value.toInt()
                if (tvHeightValue != null) tvHeightValue.setText(progress.toString() + "%")
                if (refs.currentData != null && fromUser) {
                    val height = progress / 100f
                    refs.currentData!!.height = height
                    if (refs.currentData!!.isSizeRatioLocked) {
                        refs.currentData!!.width = height
                        // Since width and height are both relative to screen height,
                        // when autoSize is enabled, they should have the same percentage value
                        if (sliderWidth != null) {
                            sliderWidth.setValue(progress.toFloat())
                        }
                    }
                    refs.notifyUpdate()
                }
            }
        }

        if (switchAutoSize != null) {
            switchAutoSize.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
                if (refs.currentData != null) {
                    refs.currentData!!.isSizeRatioLocked = isChecked
                    if (isChecked) {
                        refs.currentData!!.height = refs.currentData!!.width
                        if (sliderHeight != null) {
                            val heightPercent =
                                (refs.currentData!!.height * 100).toInt()
                            sliderHeight.setValue(heightPercent.toFloat())
                            if (tvHeightValue != null) tvHeightValue.setText(heightPercent.toString() + "%")
                        }
                    }
                    refs.notifyUpdate()
                }
            }
        }
    }

    /**
     * 绑定外观样式视图
     */
    fun bindAppearanceViews(
        view: View,
        refs: UIReferences,
        dialog: ControlEditDialogMD
    ) {
        val sliderOpacity = view.findViewById<Slider?>(R.id.seekbar_opacity)
        val tvOpacityValue = view.findViewById<TextView?>(R.id.tv_opacity_value)
        val switchVisible = view.findViewById<SwitchCompat?>(R.id.switch_visible)
        val viewBgColor = view.findViewById<View?>(R.id.view_bg_color)
        val viewStrokeColor = view.findViewById<View?>(R.id.view_stroke_color)
        val sliderCornerRadius = view.findViewById<Slider?>(R.id.seekbar_corner_radius)
        val tvCornerRadiusValue = view.findViewById<TextView?>(R.id.tv_corner_radius_value)
        val sliderStickOpacity = view.findViewById<Slider?>(R.id.seekbar_stick_opacity)
        val tvStickOpacityValue = view.findViewById<TextView?>(R.id.tv_stick_opacity_value)
        val sliderStickKnobSize = view.findViewById<Slider?>(R.id.seekbar_stick_knob_size)
        val tvStickKnobSizeValue = view.findViewById<TextView?>(R.id.tv_stick_knob_size_value)


        // 透明度设置（使用统一管理器）
        ControlEditDialogSeekBarManager.bindSeekBarSetting(
            view,
            R.id.seekbar_opacity,
            R.id.tv_opacity_value,
            ControlEditDialogSeekBarManager.createPercentConfig(
                refs.currentData!!,
                object : ValueSetter {
                    override fun get(data: ControlData?): Float {
                        return data?.opacity ?: 0.5f
                    }

                    override fun set(data: ControlData?, value: Float) {
                        data?.let { it.opacity = value }
                    }
                },
                object : UpdateNotifier {
                    override fun notifyUpdate() {
                        refs.notifyUpdate()
                    }
                }
            )
        )


        // 边框透明度设置（使用统一管理器）
        ControlEditDialogSeekBarManager.bindSeekBarSetting(
            view,
            R.id.seekbar_border_opacity,
            R.id.tv_border_opacity_value,
            ControlEditDialogSeekBarManager.createPercentConfig(
                refs.currentData!!,
                object : ValueSetter {
                    override fun get(data: ControlData?): Float {
                        // 如果为0则使用背景透明度作为兼容
                        return if (data != null && data.borderOpacity != 0f) data.borderOpacity else (data?.opacity ?: 0.5f)
                    }

                    override fun set(data: ControlData?, value: Float) {
                        data?.let { it.borderOpacity = value }
                    }
                },
                object : UpdateNotifier {
                    override fun notifyUpdate() {
                        refs.notifyUpdate()
                    }
                }
            )
        )


        // 文本透明度设置（使用统一管理器，仅按钮和文本控件显示）
        val cardTextOpacity = view.findViewById<View?>(R.id.card_text_opacity)
        if (cardTextOpacity != null && cardTextOpacity.visibility == View.VISIBLE) {
            ControlEditDialogSeekBarManager.bindSeekBarSetting(
                view,
                R.id.seekbar_text_opacity,
                R.id.tv_text_opacity_value,
                ControlEditDialogSeekBarManager.createPercentConfig(
                    refs.currentData!!,
                    object : ValueSetter {
                        override fun get(data: ControlData?): Float {
                            // 如果为0则使用背景透明度作为兼容
                            return if (data != null && data.textOpacity != 0f) data.textOpacity else (data?.opacity ?: 1.0f)
                        }

                        override fun set(data: ControlData?, value: Float) {
                            data?.let { it.textOpacity = value }
                        }
                    },
                    object : UpdateNotifier {
                        override fun notifyUpdate() {
                            refs.notifyUpdate()
                        }
                    }
                )
            )
        }

        if (switchVisible != null) {
            switchVisible.setOnCheckedChangeListener { _, isChecked ->
                refs.currentData?.let { it.isVisible = isChecked }
                refs.notifyUpdate()
            }
        }

        val itemBgColor = view.findViewById<View?>(R.id.item_bg_color)
        if (itemBgColor != null) {
            itemBgColor.setOnClickListener {
                ControlColorManager.showColorPickerDialog(
                    dialog.requireContext(),
                    refs.currentData, true,
                    object : ControlColorManager.OnColorSelectedListener {
                        override fun onColorSelected(data: ControlData?, color: Int, isBg: Boolean) {
                            val dp8 = (8 * dialog.requireContext().resources
                                .displayMetrics.density).toInt()
                            val dp2 = (2 * dialog.requireContext().resources
                                .displayMetrics.density).toInt()
                            updateColorView(viewBgColor, data!!.bgColor, dp8.toFloat(), dp2.toFloat())
                            refs.notifyUpdate()
                        }
                    })
            }
        }

        val itemStrokeColor = view.findViewById<View?>(R.id.item_stroke_color)
        if (itemStrokeColor != null) {
            itemStrokeColor.setOnClickListener {
                ControlColorManager.showColorPickerDialog(
                    dialog.requireContext(),
                    refs.currentData, false,
                    object : ControlColorManager.OnColorSelectedListener {
                        override fun onColorSelected(data: ControlData?, color: Int, isBg: Boolean) {
                            val dp8 = (8 * dialog.requireContext().resources
                                .displayMetrics.density).toInt()
                            val dp2 = (2 * dialog.requireContext().resources
                                .displayMetrics.density).toInt()
                            updateColorView(
                                viewStrokeColor,
                                data!!.strokeColor,
                                dp8.toFloat(),
                                dp2.toFloat()
                            )
                            refs.notifyUpdate()
                        }
                    })
            }
        }


        // 更新外观选项的可见性（根据控件类型和形状）
        ControlEditDialogVisibilityManager.updateAppearanceOptionsVisibility(
            view,
            refs.currentData!!
        )


        // 圆角半径设置（仅在矩形形状时显示）
        val cardCornerRadius = view.findViewById<View?>(R.id.card_corner_radius)
        if (cardCornerRadius != null && cardCornerRadius.getVisibility() == View.VISIBLE) {
            if (sliderCornerRadius != null && tvCornerRadiusValue != null) {
                if (refs.currentData != null) {
                    val cornerRadius = refs.currentData!!.cornerRadius.toInt()
                    sliderCornerRadius.setValue(cornerRadius.toFloat())
                    tvCornerRadiusValue.setText(cornerRadius.toString() + "dp")
                }

                sliderCornerRadius.addOnChangeListener { slider: Slider?, value: Float, fromUser: Boolean ->
                    val progress = value.toInt()
                    if (fromUser && refs.currentData != null) {
                        refs.currentData!!.cornerRadius = progress.toFloat()
                        tvCornerRadiusValue.setText(progress.toString() + "dp")
                        refs.notifyUpdate()
                    }
                }
            }
        }


        // 摇杆透明度设置（使用统一管理器，仅在摇杆类型时显示）
        val cardStickOpacity = view.findViewById<View?>(R.id.card_stick_opacity)
        if (cardStickOpacity != null && cardStickOpacity.visibility == View.VISIBLE) {
            ControlEditDialogSeekBarManager.bindSeekBarSetting(
                view,
                R.id.seekbar_stick_opacity,
                R.id.tv_stick_opacity_value,
                ControlEditDialogSeekBarManager.createPercentConfig(
                    refs.currentData!!,
                    object : ValueSetter {
                        override fun get(data: ControlData?): Float {
                            return if (data is ControlData.Joystick && data.stickOpacity != 0f) {
                                data.stickOpacity
                            } else {
                                1.0f
                            }
                        }

                        override fun set(data: ControlData?, value: Float) {
                            if (data is ControlData.Joystick) {
                                data.stickOpacity = value
                            }
                        }
                    },
                    object : UpdateNotifier {
                        override fun notifyUpdate() {
                            refs.notifyUpdate()
                        }
                    }
                )
            )
        }


        // 摇杆圆心大小设置（使用统一管理器，仅在摇杆类型时显示）
        val cardStickKnobSize = view.findViewById<View?>(R.id.card_stick_knob_size)
        if (cardStickKnobSize != null && cardStickKnobSize.visibility == View.VISIBLE) {
            ControlEditDialogSeekBarManager.bindSeekBarSetting(
                view,
                R.id.seekbar_stick_knob_size,
                R.id.tv_stick_knob_size_value,
                ControlEditDialogSeekBarManager.createPercentConfig(
                    refs.currentData!!,
                    object : ValueSetter {
                        override fun get(data: ControlData?): Float {
                            return if (data is ControlData.Joystick && data.stickKnobSize != 0f) {
                                data.stickKnobSize
                            } else {
                                0.4f
                            }
                        }

                        override fun set(data: ControlData?, value: Float) {
                            if (data is ControlData.Joystick) {
                                data.stickKnobSize = value
                            }
                        }
                    },
                    object : UpdateNotifier {
                        override fun notifyUpdate() {
                            refs.notifyUpdate()
                        }
                    }
                )
            )
        }
    }

    /**
     * UI元素引用接口
     * 用于传递UI元素引用给绑定方法
     */
    interface UIReferences {
        val currentData: ControlData?
        val screenWidth: Int
        val screenHeight: Int
        fun notifyUpdate()
        val context: Context

        /** 是否正在更新UI（切换控件时），此时不应修改数据  */
        val isUpdating: Boolean
    }
}

