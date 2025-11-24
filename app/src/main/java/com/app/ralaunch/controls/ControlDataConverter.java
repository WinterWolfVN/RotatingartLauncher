package com.app.ralaunch.controls;

import android.util.DisplayMetrics;
import com.app.ralaunch.model.ControlElement;

/**
 * 控件数据转换器
 * 
 * 统一管理 ControlElement 和 ControlData 之间的转换逻辑
 * 消除代码重复，确保游戏内和编辑器使用一致的数据模型
 */
public class ControlDataConverter {
    
    /**
     * 将 ControlElement 转换为 ControlData
     * 
     * @param element 控制元素（使用相对坐标 0-1）
     * @param screenWidth 屏幕宽度（像素）
     * @param screenHeight 屏幕高度（像素）
     * @return ControlData 对象（使用绝对坐标）
     */
    public static ControlData elementToData(ControlElement element, int screenWidth, int screenHeight) {
        if (element == null) {
            return null;
        }
        
        ControlData data = new ControlData();
        
        // 基本属性
        data.name = element.getName();
        data.x = element.getX() * screenWidth;  // 相对坐标转绝对坐标
        data.y = element.getY() * screenHeight;
        data.width = element.getWidth();
        data.height = element.getHeight();
        data.opacity = element.getOpacity();
        data.visible = element.getVisibility() != ControlElement.Visibility.HIDDEN;
        
        // 根据类型设置
        switch (element.getType()) {
            case BUTTON:
                data.type = ControlData.TYPE_BUTTON;
                data.keycode = element.getKeyCode();
                data.isToggle = element.isToggle();
                
                // 判断按钮模式（根据keycode范围）
                if (data.keycode <= -200 && data.keycode >= -221) {
                    data.buttonMode = ControlData.BUTTON_MODE_GAMEPAD;
                } else {
                    data.buttonMode = ControlData.BUTTON_MODE_KEYBOARD;
                }
                break;
                
            case JOYSTICK:
                data.type = ControlData.TYPE_JOYSTICK;
                int keyCode = element.getKeyCode();
                
                // 根据keycode判断摇杆模式
                if (keyCode == -300) {
                    // 左摇杆（SDL控制器模式）
                    data.joystickMode = ControlData.JOYSTICK_MODE_SDL_CONTROLLER;
                    data.xboxUseRightStick = false;
                } else if (keyCode == -301) {
                    // 右摇杆（SDL控制器模式）
                    data.joystickMode = ControlData.JOYSTICK_MODE_SDL_CONTROLLER;
                    data.xboxUseRightStick = true;
                } else {
                    // 键盘模式
                    data.joystickMode = ControlData.JOYSTICK_MODE_KEYBOARD;
                    data.joystickKeys = new int[]{
                        ControlData.SDL_SCANCODE_W,
                        ControlData.SDL_SCANCODE_D,
                        ControlData.SDL_SCANCODE_S,
                        ControlData.SDL_SCANCODE_A
                    };
                }
                break;
                
            case CROSS_KEY:
            case TRIGGER_BUTTON:
            case TOUCHPAD:
            case MOUSE_AREA:
            case MACRO_BUTTON:
            case GROUP:
            default:
                // 不支持的类型，返回 null
                return null;
        }
        
        // 外观属性
        data.bgColor = element.getBackgroundColor();
        data.strokeColor = element.getBorderColor();
        data.strokeWidth = element.getBorderWidth();
        data.cornerRadius = element.getCornerRadius();
        
        return data;
    }
    
    /**
     * 将 ControlData 转换为 ControlElement
     * 
     * @param data ControlData 对象（使用绝对坐标）
     * @param screenWidth 屏幕宽度（像素）
     * @param screenHeight 屏幕高度（像素）
     * @return ControlElement 对象（使用相对坐标 0-1）
     */
    public static ControlElement dataToElement(ControlData data, int screenWidth, int screenHeight) {
        if (data == null || screenWidth <= 0 || screenHeight <= 0) {
            return null;
        }
        
        ControlElement.ElementType type;
        
        if (data.type == ControlData.TYPE_BUTTON) {
            type = ControlElement.ElementType.BUTTON;
        } else if (data.type == ControlData.TYPE_JOYSTICK) {
            type = ControlElement.ElementType.JOYSTICK;
        } else {
            type = ControlElement.ElementType.BUTTON;
        }
        
        ControlElement element = new ControlElement(
            data.name != null ? data.name : "控件",
            type,
            data.name != null ? data.name : "控件"
        );
        
        // 位置和大小（绝对坐标转相对坐标）
        element.setX(data.x / screenWidth);
        element.setY(data.y / screenHeight);
        element.setWidth(data.width);
        element.setHeight(data.height);
        element.setOpacity(data.opacity);
        element.setVisibility(data.visible ? ControlElement.Visibility.ALWAYS : ControlElement.Visibility.HIDDEN);
        
        // 按键设置
        if (type == ControlElement.ElementType.BUTTON) {
            element.setKeyCode(data.keycode);
            element.setToggle(data.isToggle);
        } else if (type == ControlElement.ElementType.JOYSTICK) {
            // 摇杆模式转换
            if (data.joystickMode == ControlData.JOYSTICK_MODE_SDL_CONTROLLER) {
                element.setKeyCode(data.xboxUseRightStick ? -301 : -300);
            } else {
                element.setKeyCode(data.joystickKeys != null && data.joystickKeys.length > 0 ? data.joystickKeys[0] : 0);
            }
        }
        
        // 外观属性
        element.setBackgroundColor(data.bgColor);
        element.setBorderColor(data.strokeColor);
        element.setBorderWidth(data.strokeWidth);
        element.setCornerRadius(data.cornerRadius);
        
        return element;
    }
    
    /**
     * 使用 DisplayMetrics 进行转换（便捷方法）
     */
    public static ControlData elementToData(ControlElement element, DisplayMetrics metrics) {
        return elementToData(element, metrics.widthPixels, metrics.heightPixels);
    }
    
    /**
     * 使用 DisplayMetrics 进行转换（便捷方法）
     */
    public static ControlElement dataToElement(ControlData data, DisplayMetrics metrics) {
        return dataToElement(data, metrics.widthPixels, metrics.heightPixels);
    }
}

