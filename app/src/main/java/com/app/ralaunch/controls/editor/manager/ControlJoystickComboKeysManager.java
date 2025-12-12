package com.app.ralaunch.controls.editor.manager;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlData;
import com.app.ralaunch.controls.KeyMapper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 摇杆组合键管理器
 * 统一管理摇杆方向组合键的选择和显示逻辑
 */
public class ControlJoystickComboKeysManager {
    
    // 方向名称数组（对应 VirtualJoystick 的8个方向）
    // 注意：这些名称需要从 Context 获取，不能作为静态常量
    // 使用 getDirectionName(Context, int) 方法获取本地化的方向名称
    
    // 所有可用的手柄按钮（用于组合键选择）
    // 使用 KeyMapper.getXboxButtons() 获取，确保与系统一致
    
    /**
     * 获取方向显示名称
     */
    public static String getDirectionName(Context context, int direction) {
        int resId;
        switch (direction) {
            case 0: resId = R.string.control_direction_up; break;
            case 1: resId = R.string.control_direction_up_right; break;
            case 2: resId = R.string.control_direction_right; break;
            case 3: resId = R.string.control_direction_down_right; break;
            case 4: resId = R.string.control_direction_down; break;
            case 5: resId = R.string.control_direction_down_left; break;
            case 6: resId = R.string.control_direction_left; break;
            case 7: resId = R.string.control_direction_up_left; break;
            default: return context.getString(R.string.control_unknown);
        }
        return context.getString(resId);
    }
    
    /**
     * 获取组合键显示文本
     * @param context 上下文（用于获取本地化字符串）
     * @param comboKeys 组合键数组
     * @return 显示文本，如 "R+B" 或 "无"
     */
    public static String getComboKeysDisplayText(Context context, int[] comboKeys) {
        if (comboKeys == null || comboKeys.length == 0) {
            return context.getString(R.string.control_none);
        }
        
        List<String> buttonNames = new ArrayList<>();
        for (int button : comboKeys) {
            String name = KeyMapper.getKeyName(button);
            if (name != null && !name.isEmpty()) {
                buttonNames.add(name);
            }
        }
        
        if (buttonNames.isEmpty()) {
            return context.getString(R.string.control_none);
        }
        
        return String.join("+", buttonNames);
    }
    
    /**
     * 显示统一组合键选择对话框（所有方向共用）
     * @param context 上下文
     * @param data 控件数据
     * @param listener 选择监听器
     */
    public static void showComboKeysSelectDialog(@NonNull Context context,
                                                ControlData data,
                                                OnComboKeysSelectedListener listener) {
        if (data == null || data.type != ControlData.TYPE_JOYSTICK) {
            return;
        }
        
        // 确保组合键数组已初始化
        if (data.joystickComboKeys == null) {
            data.joystickComboKeys = new int[0];
        }
        
        int[] currentComboKeys = data.joystickComboKeys;
        
        // 构建按键名称列表（包括键盘、鼠标、手柄按钮和触发器）
        List<String> buttonNames = new ArrayList<>();
        List<Integer> buttonValues = new ArrayList<>();
        
        // 获取所有可用的按键（包括键盘、鼠标、手柄按钮和触发器）
        java.util.Map<String, Integer> allKeys = KeyMapper.getAllKeys();
        for (java.util.Map.Entry<String, Integer> entry : allKeys.entrySet()) {
            int keyValue = entry.getValue();
            // 排除特殊功能按键（如"键盘"）
            if (keyValue != ControlData.SPECIAL_KEYBOARD) {
                buttonNames.add(entry.getKey());
                buttonValues.add(keyValue);
            }
        }
        
        // 构建多选数组（标记当前选中的按钮）
        boolean[] checkedItems = new boolean[buttonNames.size()];
        for (int i = 0; i < buttonValues.size(); i++) {
            for (int currentKey : currentComboKeys) {
                if (buttonValues.get(i) == currentKey) {
                    checkedItems[i] = true;
                    break;
                }
            }
        }
        
        String[] buttonNamesArray = buttonNames.toArray(new String[0]);
        
        new MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.editor_select_combo_keys))
            .setMultiChoiceItems(buttonNamesArray, checkedItems, (dialog, which, isChecked) -> {
                // 多选状态变化时更新数组
                checkedItems[which] = isChecked;
            })
            .setPositiveButton(context.getString(R.string.ok), (dialog, which) -> {
                // 收集选中的按钮
                List<Integer> selectedButtons = new ArrayList<>();
                for (int i = 0; i < checkedItems.length; i++) {
                    if (checkedItems[i]) {
                        selectedButtons.add(buttonValues.get(i));
                    }
                }
                
                // 更新统一组合键数组（所有方向共用）
                int[] newComboKeys = new int[selectedButtons.size()];
                for (int i = 0; i < selectedButtons.size(); i++) {
                    newComboKeys[i] = selectedButtons.get(i);
                }
                data.joystickComboKeys = newComboKeys;
                
                if (listener != null) {
                    listener.onComboKeysSelected(data);
                }
            })
            .setNegativeButton(context.getString(R.string.cancel), null)
            .setNeutralButton(context.getString(R.string.control_clear), (dialog, which) -> {
                // 清除统一组合键
                data.joystickComboKeys = new int[0];
                
                if (listener != null) {
                    listener.onComboKeysSelected(data);
                }
            })
            .show();
    }
    
    
    /**
     * 更新统一组合键显示
     */
    public static void updateComboKeysDisplay(Context context, ControlData data, TextView textView) {
        if (data == null || textView == null) {
            return;
        }
        
        String displayText = getComboKeysDisplayText(context, data.joystickComboKeys);
        textView.setText(displayText);
    }
    
    /**
     * 组合键选择监听器
     */
    public interface OnComboKeysSelectedListener {
        void onComboKeysSelected(ControlData data);
    }
}

