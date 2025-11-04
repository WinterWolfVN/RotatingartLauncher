package com.app.ralaunch.controls;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按键映射辅助类
 * 提供按键码和按键名称的映射关系
 */
public class KeyMapper {
    
    /**
     * 获取所有可用的按键映射
     */
    public static Map<String, Integer> getAllKeys() {
        Map<String, Integer> keys = new LinkedHashMap<>();
        
        // 特殊功能
        keys.put("⌨️ 键盘", ControlData.SPECIAL_KEYBOARD);
        
        // 鼠标按键
        keys.put("鼠标左键", ControlData.MOUSE_LEFT);
        keys.put("鼠标右键", ControlData.MOUSE_RIGHT);
        keys.put("鼠标中键", ControlData.MOUSE_MIDDLE);
        
        // 常用键盘按键
        keys.put("空格", ControlData.SDL_SCANCODE_SPACE);
        keys.put("回车", ControlData.SDL_SCANCODE_RETURN);
        keys.put("ESC", ControlData.SDL_SCANCODE_ESCAPE);
        
        // 字母键 (完整的A-Z)
        keys.put("A", 4);   // SDL_SCANCODE_A
        keys.put("B", 5);   // SDL_SCANCODE_B
        keys.put("C", 6);   // SDL_SCANCODE_C
        keys.put("D", 7);   // SDL_SCANCODE_D
        keys.put("E", 8);   // SDL_SCANCODE_E
        keys.put("F", 9);   // SDL_SCANCODE_F
        keys.put("G", 10);  // SDL_SCANCODE_G
        keys.put("H", 11);  // SDL_SCANCODE_H
        keys.put("I", 12);  // SDL_SCANCODE_I
        keys.put("J", 13);  // SDL_SCANCODE_J
        keys.put("K", 14);  // SDL_SCANCODE_K
        keys.put("L", 15);  // SDL_SCANCODE_L
        keys.put("M", 16);  // SDL_SCANCODE_M
        keys.put("N", 17);  // SDL_SCANCODE_N
        keys.put("O", 18);  // SDL_SCANCODE_O
        keys.put("P", 19);  // SDL_SCANCODE_P
        keys.put("Q", 20);  // SDL_SCANCODE_Q
        keys.put("R", 21);  // SDL_SCANCODE_R
        keys.put("S", 22);  // SDL_SCANCODE_S
        keys.put("T", 23);  // SDL_SCANCODE_T
        keys.put("U", 24);  // SDL_SCANCODE_U
        keys.put("V", 25);  // SDL_SCANCODE_V
        keys.put("W", 26);  // SDL_SCANCODE_W
        keys.put("X", 27);  // SDL_SCANCODE_X
        keys.put("Y", 28);  // SDL_SCANCODE_Y
        keys.put("Z", 29);  // SDL_SCANCODE_Z
        
        // 数字键
        keys.put("1", 30);  // SDL_SCANCODE_1
        keys.put("2", 31);  // SDL_SCANCODE_2
        keys.put("3", 32);  // SDL_SCANCODE_3
        keys.put("4", 33);  // SDL_SCANCODE_4
        keys.put("5", 34);  // SDL_SCANCODE_5
        keys.put("6", 35);  // SDL_SCANCODE_6
        keys.put("7", 36);  // SDL_SCANCODE_7
        keys.put("8", 37);  // SDL_SCANCODE_8
        keys.put("9", 38);  // SDL_SCANCODE_9
        keys.put("0", 39);  // SDL_SCANCODE_0
        
        // 功能键 (F1-F12)
        keys.put("F1", 58);   // SDL_SCANCODE_F1
        keys.put("F2", 59);   // SDL_SCANCODE_F2
        keys.put("F3", 60);   // SDL_SCANCODE_F3
        keys.put("F4", 61);   // SDL_SCANCODE_F4
        keys.put("F5", 62);   // SDL_SCANCODE_F5
        keys.put("F6", 63);   // SDL_SCANCODE_F6
        keys.put("F7", 64);   // SDL_SCANCODE_F7
        keys.put("F8", 65);   // SDL_SCANCODE_F8
        keys.put("F9", 66);   // SDL_SCANCODE_F9
        keys.put("F10", 67);  // SDL_SCANCODE_F10
        keys.put("F11", 68);  // SDL_SCANCODE_F11
        keys.put("F12", 69);  // SDL_SCANCODE_F12
        
        // 修饰键 (左侧)
        keys.put("Shift (左)", 225);  // SDL_SCANCODE_LSHIFT
        keys.put("Ctrl (左)", 224);   // SDL_SCANCODE_LCTRL
        keys.put("Alt (左)", 226);    // SDL_SCANCODE_LALT
        
        // 修饰键 (右侧)
        keys.put("Shift (右)", 229);  // SDL_SCANCODE_RSHIFT
        keys.put("Ctrl (右)", 228);   // SDL_SCANCODE_RCTRL
        keys.put("Alt (右)", 230);    // SDL_SCANCODE_RALT
        
        // 其他常用键
        keys.put("Tab", 43);          // SDL_SCANCODE_TAB
        keys.put("Caps Lock", 57);    // SDL_SCANCODE_CAPSLOCK
        keys.put("Backspace", 42);    // SDL_SCANCODE_BACKSPACE
        keys.put("Delete", 76);       // SDL_SCANCODE_DELETE
        keys.put("Insert", 73);       // SDL_SCANCODE_INSERT
        keys.put("Home", 74);         // SDL_SCANCODE_HOME
        keys.put("End", 77);          // SDL_SCANCODE_END
        keys.put("Page Up", 75);      // SDL_SCANCODE_PAGEUP
        keys.put("Page Down", 78);    // SDL_SCANCODE_PAGEDOWN
        
        // 方向键
        keys.put("↑ 上", 82);  // SDL_SCANCODE_UP
        keys.put("↓ 下", 81);  // SDL_SCANCODE_DOWN
        keys.put("← 左", 80);  // SDL_SCANCODE_LEFT
        keys.put("→ 右", 79);  // SDL_SCANCODE_RIGHT
        
        // 符号键
        keys.put("-", 45);    // SDL_SCANCODE_MINUS
        keys.put("=", 46);    // SDL_SCANCODE_EQUALS
        keys.put("[", 47);    // SDL_SCANCODE_LEFTBRACKET
        keys.put("]", 48);    // SDL_SCANCODE_RIGHTBRACKET
        keys.put("\\", 49);   // SDL_SCANCODE_BACKSLASH
        keys.put(";", 51);    // SDL_SCANCODE_SEMICOLON
        keys.put("'", 52);    // SDL_SCANCODE_APOSTROPHE
        keys.put("`", 53);    // SDL_SCANCODE_GRAVE
        keys.put(",", 54);    // SDL_SCANCODE_COMMA
        keys.put(".", 55);    // SDL_SCANCODE_PERIOD
        keys.put("/", 56);    // SDL_SCANCODE_SLASH
        
        // 小键盘数字键
        keys.put("小键盘 0", 98);   // SDL_SCANCODE_KP_0
        keys.put("小键盘 1", 89);   // SDL_SCANCODE_KP_1
        keys.put("小键盘 2", 90);   // SDL_SCANCODE_KP_2
        keys.put("小键盘 3", 91);   // SDL_SCANCODE_KP_3
        keys.put("小键盘 4", 92);   // SDL_SCANCODE_KP_4
        keys.put("小键盘 5", 93);   // SDL_SCANCODE_KP_5
        keys.put("小键盘 6", 94);   // SDL_SCANCODE_KP_6
        keys.put("小键盘 7", 95);   // SDL_SCANCODE_KP_7
        keys.put("小键盘 8", 96);   // SDL_SCANCODE_KP_8
        keys.put("小键盘 9", 97);   // SDL_SCANCODE_KP_9
        
        // 小键盘功能键
        keys.put("小键盘 +", 87);   // SDL_SCANCODE_KP_PLUS
        keys.put("小键盘 -", 86);   // SDL_SCANCODE_KP_MINUS
        keys.put("小键盘 *", 85);   // SDL_SCANCODE_KP_MULTIPLY
        keys.put("小键盘 /", 84);   // SDL_SCANCODE_KP_DIVIDE
        keys.put("小键盘 .", 99);   // SDL_SCANCODE_KP_PERIOD
        keys.put("小键盘 Enter", 88); // SDL_SCANCODE_KP_ENTER
        
        return keys;
    }
    
    /**
     * 根据按键码获取按键名称
     */
    public static String getKeyName(int keycode) {
        for (Map.Entry<String, Integer> entry : getAllKeys().entrySet()) {
            if (entry.getValue() == keycode) {
                return entry.getKey();
            }
        }
        return "未知 (" + keycode + ")";
    }
    
    /**
     * 获取游戏常用按键（用于快速选择）
     */
    public static Map<String, Integer> getGameKeys() {
        Map<String, Integer> keys = new LinkedHashMap<>();
        keys.put("鼠标左键 (攻击)", ControlData.MOUSE_LEFT);
        keys.put("鼠标右键 (使用)", ControlData.MOUSE_RIGHT);
        keys.put("空格 (跳跃)", ControlData.SDL_SCANCODE_SPACE);
        keys.put("E (钩爪)", ControlData.SDL_SCANCODE_E);
        keys.put("H (药水)", ControlData.SDL_SCANCODE_H);
        keys.put("ESC (菜单)", ControlData.SDL_SCANCODE_ESCAPE);
        keys.put("Shift (冲刺)", ControlData.SDL_SCANCODE_LSHIFT);
        keys.put("Ctrl (智能光标)", ControlData.SDL_SCANCODE_LCTRL);
        return keys;
    }
}

