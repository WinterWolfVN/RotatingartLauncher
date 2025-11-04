package com.app.ralaunch.controls;

import android.util.Log;
import org.libsdl.app.SDLActivity;

/**
 * SDL输入桥接实现
 * 将虚拟控制器输入转发到SDL
 */
public class SDLInputBridge implements ControlInputBridge {
    private static final String TAG = "SDLInputBridge";
    
    // Mouse actions
    private static final int ACTION_DOWN = 0;
    private static final int ACTION_UP = 1;
    private static final int ACTION_MOVE = 2;
    
    // Mouse buttons
    private static final int BUTTON_LEFT = 1;
    private static final int BUTTON_RIGHT = 3;
    private static final int BUTTON_MIDDLE = 2;
    
    /**
     * 将SDL Scancode转换为Android KeyCode
     * SDLActivity.onNativeKeyDown期望接收Android KeyCode（如KEYCODE_A=29），不是ASCII！
     */
    private int scancodeToKeycode(int scancode) {
        switch (scancode) {
            // 字母键 (SDL Scancode -> Android KeyCode)
            case 4: return android.view.KeyEvent.KEYCODE_A;    // 29
            case 5: return android.view.KeyEvent.KEYCODE_B;    // 30
            case 6: return android.view.KeyEvent.KEYCODE_C;    // 31
            case 7: return android.view.KeyEvent.KEYCODE_D;    // 32
            case 8: return android.view.KeyEvent.KEYCODE_E;    // 33
            case 9: return android.view.KeyEvent.KEYCODE_F;    // 34
            case 10: return android.view.KeyEvent.KEYCODE_G;   // 35
            case 11: return android.view.KeyEvent.KEYCODE_H;   // 36
            case 12: return android.view.KeyEvent.KEYCODE_I;   // 37
            case 13: return android.view.KeyEvent.KEYCODE_J;   // 38
            case 14: return android.view.KeyEvent.KEYCODE_K;   // 39
            case 15: return android.view.KeyEvent.KEYCODE_L;   // 40
            case 16: return android.view.KeyEvent.KEYCODE_M;   // 41
            case 17: return android.view.KeyEvent.KEYCODE_N;   // 42
            case 18: return android.view.KeyEvent.KEYCODE_O;   // 43
            case 19: return android.view.KeyEvent.KEYCODE_P;   // 44
            case 20: return android.view.KeyEvent.KEYCODE_Q;   // 45
            case 21: return android.view.KeyEvent.KEYCODE_R;   // 46
            case 22: return android.view.KeyEvent.KEYCODE_S;   // 47
            case 23: return android.view.KeyEvent.KEYCODE_T;   // 48
            case 24: return android.view.KeyEvent.KEYCODE_U;   // 49
            case 25: return android.view.KeyEvent.KEYCODE_V;   // 50
            case 26: return android.view.KeyEvent.KEYCODE_W;   // 51
            case 27: return android.view.KeyEvent.KEYCODE_X;   // 52
            case 28: return android.view.KeyEvent.KEYCODE_Y;   // 53
            case 29: return android.view.KeyEvent.KEYCODE_Z;   // 54
            
            // 数字键 (SDL Scancode -> Android KeyCode)
            case 30: return android.view.KeyEvent.KEYCODE_1;   // 8
            case 31: return android.view.KeyEvent.KEYCODE_2;   // 9
            case 32: return android.view.KeyEvent.KEYCODE_3;   // 10
            case 33: return android.view.KeyEvent.KEYCODE_4;   // 11
            case 34: return android.view.KeyEvent.KEYCODE_5;   // 12
            case 35: return android.view.KeyEvent.KEYCODE_6;   // 13
            case 36: return android.view.KeyEvent.KEYCODE_7;   // 14
            case 37: return android.view.KeyEvent.KEYCODE_8;   // 15
            case 38: return android.view.KeyEvent.KEYCODE_9;   // 16
            case 39: return android.view.KeyEvent.KEYCODE_0;   // 7
            
            // 特殊键
            case 40: return android.view.KeyEvent.KEYCODE_ENTER;       // 66
            case 41: return android.view.KeyEvent.KEYCODE_ESCAPE;      // 111
            case 42: return android.view.KeyEvent.KEYCODE_DEL;         // 67 (Backspace)
            case 43: return android.view.KeyEvent.KEYCODE_TAB;         // 61
            case 44: return android.view.KeyEvent.KEYCODE_SPACE;       // 62
            
            // 方向键
            case 79: return android.view.KeyEvent.KEYCODE_DPAD_RIGHT;  // 22
            case 80: return android.view.KeyEvent.KEYCODE_DPAD_LEFT;   // 21
            case 81: return android.view.KeyEvent.KEYCODE_DPAD_DOWN;   // 20
            case 82: return android.view.KeyEvent.KEYCODE_DPAD_UP;     // 19
            
            default:
                Log.w(TAG, "Unknown scancode: " + scancode + ", passing through");
                return scancode; // 未知的直接传递
        }
    }
    
    @Override
    public void sendKey(int scancode, boolean isDown) {
        try {
            // 将Scancode转换为Keycode
            int keycode = scancodeToKeycode(scancode);
            
            // 调用SDLActivity的静态native方法
            if (isDown) {
                SDLActivity.onNativeKeyDown(keycode);
                Log.d(TAG, "Key DOWN: scancode=" + scancode + " -> keycode=" + keycode);
            } else {
                SDLActivity.onNativeKeyUp(keycode);
                Log.d(TAG, "Key UP: scancode=" + scancode + " -> keycode=" + keycode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending key: " + scancode, e);
        }
    }
    
    @Override
    public void sendMouseButton(int button, boolean isDown, float x, float y) {
        try {
            int sdlButton;
            switch (button) {
                case ControlData.MOUSE_LEFT:
                    sdlButton = BUTTON_LEFT;
                    break;
                case ControlData.MOUSE_RIGHT:
                    sdlButton = BUTTON_RIGHT;
                    break;
                case ControlData.MOUSE_MIDDLE:
                    sdlButton = BUTTON_MIDDLE;
                    break;
                default:
                    Log.w(TAG, "Unknown mouse button: " + button);
                    return;
            }
            
            int action = isDown ? ACTION_DOWN : ACTION_UP;
            // 调用SDLActivity的静态native方法，传递按钮中心坐标
            SDLActivity.onNativeMouse(sdlButton, action, x, y, false);
            Log.d(TAG, "Mouse button " + sdlButton + " " + (isDown ? "DOWN" : "UP") + " at (" + x + ", " + y + ")");
        } catch (Exception e) {
            Log.e(TAG, "Error sending mouse button: " + button, e);
        }
    }
    
    @Override
    public void sendMouseMove(float deltaX, float deltaY) {
        try {
            // 调用SDLActivity的静态native方法
            SDLActivity.onNativeMouse(0, ACTION_MOVE, deltaX, deltaY, true);
        } catch (Exception e) {
            Log.e(TAG, "Error sending mouse move", e);
        }
    }
}

