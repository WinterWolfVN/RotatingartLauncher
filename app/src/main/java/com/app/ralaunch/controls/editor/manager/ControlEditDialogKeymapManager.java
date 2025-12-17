package com.app.ralaunch.controls.editor.manager;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlData;
import com.app.ralaunch.controls.KeyMapper;
import com.app.ralaunch.controls.editor.ControlEditDialogMD;
import com.app.ralaunch.controls.editor.JoystickKeyMappingDialog;
import com.app.ralaunch.controls.editor.KeySelectorDialog;
import com.app.ralaunch.controls.editor.manager.ControlEditDialogVisibilityManager;

/**
 * 键值设置管理器
 * 统一管理普通按钮和摇杆的键值设置逻辑
 */
public class ControlEditDialogKeymapManager {
    
    /**
     * UI元素引用接口
     */
    public interface UIReferences {
        ControlData getCurrentData();
        void notifyUpdate();
    }
    
    /**
     * 绑定键值设置视图
     */
    public static void bindKeymapViews(@NonNull View view, 
                                        @NonNull UIReferences refs,
                                        @NonNull ControlEditDialogMD dialog) {
        View itemKeyMapping = view.findViewById(R.id.item_key_mapping);
        TextView tvKeyName = view.findViewById(R.id.tv_key_name);
        SwitchCompat switchToggleMode = view.findViewById(R.id.switch_toggle_mode);
        
        // 普通按钮的键值映射
        if (itemKeyMapping != null) {
            itemKeyMapping.setOnClickListener(v -> showKeySelectDialog(dialog, refs, tvKeyName));
        }
        
        // 摇杆的键值映射
        View itemJoystickKeyMapping = view.findViewById(R.id.item_joystick_key_mapping);
        TextView tvJoystickKeyMappingValue = view.findViewById(R.id.tv_joystick_key_mapping_value);
        if (itemJoystickKeyMapping != null) {
            itemJoystickKeyMapping.setOnClickListener(v -> showJoystickKeyMappingDialog(dialog, refs, tvJoystickKeyMappingValue));
        }
        
        if (switchToggleMode != null) {
            switchToggleMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (refs.getCurrentData() != null) {
                    refs.getCurrentData().isToggle = isChecked;
                    refs.notifyUpdate();
                }
            });
        }
    }
    
    /**
     * 显示摇杆键值映射对话框
     */
    private static void showJoystickKeyMappingDialog(@NonNull ControlEditDialogMD dialog,
                                                     @NonNull UIReferences refs,
                                                     TextView tvJoystickKeyMappingValue) {
        ControlData data = refs.getCurrentData();
        if (data == null || data.type != ControlData.TYPE_JOYSTICK) {
            return;
        }
        
        // 确保 joystickKeys 存在
        if (data.joystickKeys == null || data.joystickKeys.length < 4) {
            data.joystickKeys = new int[]{
                ControlData.SDL_SCANCODE_W,  // up
                ControlData.SDL_SCANCODE_D,  // right
                ControlData.SDL_SCANCODE_S,  // down
                ControlData.SDL_SCANCODE_A   // left
            };
        }
        
        JoystickKeyMappingDialog keyMappingDialog = new JoystickKeyMappingDialog(
            dialog.getContext(), data, (updatedData) -> {
                // 更新数据
                data.joystickKeys = updatedData.joystickKeys;
                
                // 更新显示
                updateJoystickKeyMappingDisplay(tvJoystickKeyMappingValue, data);
                
                refs.notifyUpdate();
            });
        
        keyMappingDialog.show();
    }
    
    /**
     * 更新摇杆键值映射显示
     */
    private static void updateJoystickKeyMappingDisplay(TextView tv, ControlData data) {
        if (tv == null || data == null || data.joystickKeys == null || data.joystickKeys.length < 4) {
            return;
        }
        
        String up = KeyMapper.getKeyName(data.joystickKeys[0]);
        String right = KeyMapper.getKeyName(data.joystickKeys[1]);
        String down = KeyMapper.getKeyName(data.joystickKeys[2]);
        String left = KeyMapper.getKeyName(data.joystickKeys[3]);
        
        String displayText = tv.getContext().getString(R.string.editor_joystick_key_mapping_value, up, right, down, left);
        tv.setText(displayText);
    }
    
    /**
     * 显示按键选择对话框
     */
    private static void showKeySelectDialog(@NonNull ControlEditDialogMD dialog,
                                            @NonNull UIReferences refs,
                                            TextView tvKeyName) {
        ControlData data = refs.getCurrentData();
        if (data == null) return;
        
        boolean isGamepadMode = (data.buttonMode == ControlData.BUTTON_MODE_GAMEPAD);
        KeySelectorDialog keyDialog = new KeySelectorDialog(dialog.getContext(), isGamepadMode);
        
        keyDialog.setOnKeySelectedListener((keycode, keyName) -> {
            data.keycode = keycode;
            // 使用 KeyMapper 获取完整的按键名称，确保显示正确
            String fullKeyName = KeyMapper.getKeyName(keycode);
            if (tvKeyName != null) {
                tvKeyName.setText(fullKeyName);
            }
            refs.notifyUpdate();
        });
        
        keyDialog.show();
    }
    
    /**
     * 更新键值设置视图的可见性
     */
    public static void updateKeymapVisibility(@NonNull View keymapView, @NonNull ControlData data) {
        View itemKeyMapping = keymapView.findViewById(R.id.item_key_mapping);
        View itemJoystickKeyMapping = keymapView.findViewById(R.id.item_joystick_key_mapping);
        View itemToggleMode = keymapView.findViewById(R.id.item_toggle_mode);
        TextView tvJoystickKeyMappingValue = keymapView.findViewById(R.id.tv_joystick_key_mapping_value);
        
        // 普通按钮：显示单个按键映射（文本控件和摇杆不支持单个按键映射）
        boolean isButton = (data.type == ControlData.TYPE_BUTTON);
        boolean isText = (data.type == ControlData.TYPE_TEXT);
        boolean isJoystick = (data.type == ControlData.TYPE_JOYSTICK);
        
        // 普通按钮的键值映射：只有按钮类型（非文本、非摇杆）才显示
        boolean shouldShowButtonKeymap = isButton && !isText && !isJoystick;
        
        // 摇杆的键值映射：只有摇杆类型且为键盘模式才显示
        boolean shouldShowJoystickKeymap = isJoystick && 
            data.joystickMode == ControlData.JOYSTICK_MODE_KEYBOARD;
        
        if (itemKeyMapping != null) {
            itemKeyMapping.setVisibility(shouldShowButtonKeymap ? View.VISIBLE : View.GONE);
        }
        if (itemJoystickKeyMapping != null) {
            itemJoystickKeyMapping.setVisibility(shouldShowJoystickKeymap ? View.VISIBLE : View.GONE);
            // 更新显示值
            if (shouldShowJoystickKeymap && tvJoystickKeyMappingValue != null) {
                updateJoystickKeyMappingDisplay(tvJoystickKeyMappingValue, data);
            }
        }
        if (itemToggleMode != null) {
            itemToggleMode.setVisibility(shouldShowButtonKeymap ? View.VISIBLE : View.GONE);
        }
    }
}

