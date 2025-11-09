package com.app.ralaunch.controls;

/**
 * 输入桥接接口
 * 将虚拟控制器的输入转发到SDL/游戏
 */
public interface ControlInputBridge {
    /**
     * 发送键盘按键事件
     * @param keycode SDL按键码
     * @param isDown true=按下, false=释放
     */
    void sendKey(int keycode, boolean isDown);
    
    /**
     * 发送鼠标按键事件
     * @param button ControlData.MOUSE_LEFT/RIGHT/MIDDLE
     * @param isDown true=按下, false=释放
     * @param x 鼠标X坐标（屏幕坐标）
     * @param y 鼠标Y坐标（屏幕坐标）
     */
    void sendMouseButton(int button, boolean isDown, float x, float y);
    
    /**
     * 发送鼠标移动事件
     * @param deltaX X轴移动量
     * @param deltaY Y轴移动量
     */
    void sendMouseMove(float deltaX, float deltaY);
}
