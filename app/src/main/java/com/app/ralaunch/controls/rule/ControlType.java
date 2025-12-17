package com.app.ralaunch.controls.rule;

/**
 * 控件类型枚举
 */
public enum ControlType {
    /** 虚拟按钮 */
    VIRTUAL_BUTTON(1),

    /** 虚拟摇杆 */
    VIRTUAL_JOYSTICK(2),

    /** 虚拟鼠标 */
    VIRTUAL_MOUSE(3),

    /** 游戏原生触摸（直接透传给SDL） */
    GAME_NATIVE_TOUCH(4),

    /** 未知类型 */
    UNKNOWN(0);

    private final int priority;

    ControlType(int priority) {
        this.priority = priority;
    }

    /**
     * 获取优先级（数值越大优先级越高）
     * 虚拟按钮 > 虚拟摇杆 > 虚拟鼠标 > 游戏原生触摸
     */
    public int getPriority() {
        return priority;
    }
}
