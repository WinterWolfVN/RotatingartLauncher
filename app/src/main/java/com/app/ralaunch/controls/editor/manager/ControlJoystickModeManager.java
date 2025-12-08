package com.app.ralaunch.controls.editor.manager;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.app.ralaunch.controls.ControlData;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 摇杆模式管理器
 * 统一管理摇杆模式的选择和显示逻辑
 */
public class ControlJoystickModeManager {

    /**
     * 获取摇杆模式显示名称
     */
    public static String getModeDisplayName(int joystickMode) {
        switch (joystickMode) {
            case ControlData.JOYSTICK_MODE_KEYBOARD:
                return "键盘模式";
            case ControlData.JOYSTICK_MODE_MOUSE:
                return "鼠标模式";
            case ControlData.JOYSTICK_MODE_SDL_CONTROLLER:
                return "XBOX控制器模式";
            default:
                return "键盘模式";
        }
    }

    /**
     * 更新模式显示
     */
    public static void updateModeDisplay(ControlData data, TextView textView) {
        if (data == null || textView == null) {
            return;
        }
        
        String modeName = getModeDisplayName(data.joystickMode);
        textView.setText(modeName);
    }

    /**
     * 显示摇杆模式选择对话框
     */
    public static void showModeSelectDialog(@NonNull Context context, 
                                           ControlData data,
                                           OnModeSelectedListener listener) {
        if (data == null || data.type != ControlData.TYPE_JOYSTICK) {
            return;
        }

        String[] modes = {"键盘模式", "鼠标模式", "XBOX控制器模式"};
        
        // 确定当前选中的索引
        int currentIndex = data.joystickMode;
        if (currentIndex < 0 || currentIndex >= modes.length) {
            currentIndex = ControlData.JOYSTICK_MODE_KEYBOARD;
        }

        new MaterialAlertDialogBuilder(context)
            .setTitle("选择摇杆模式")
            .setSingleChoiceItems(modes, currentIndex, (dialog, which) -> {
                int newMode = which;
                data.joystickMode = newMode;
                
                // 根据模式设置相关属性
                if (newMode == ControlData.JOYSTICK_MODE_KEYBOARD) {
                    // 键盘模式：需要设置 joystickKeys
                    if (data.joystickKeys == null) {
                        data.joystickKeys = new int[]{
                            ControlData.SDL_SCANCODE_W,  // up
                            ControlData.SDL_SCANCODE_D,  // right
                            ControlData.SDL_SCANCODE_S,  // down
                            ControlData.SDL_SCANCODE_A   // left
                        };
                    }
                } else if (newMode == ControlData.JOYSTICK_MODE_MOUSE) {
                    // 鼠标模式：不需要 joystickKeys
                    data.joystickKeys = null;
                } else if (newMode == ControlData.JOYSTICK_MODE_SDL_CONTROLLER) {
                    data.joystickKeys = null;
                }
                
                if (listener != null) {
                    listener.onModeSelected(data);
                }
                
                dialog.dismiss();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 模式选择监听器
     */
    public interface OnModeSelectedListener {
        void onModeSelected(ControlData data);
    }
}

