package com.app.ralaunch.controls;

/**
 * 虚拟控制View接口
 * 所有虚拟控制元素（按钮、摇杆等）都实现此接口
 */
public interface ControlView {
    /**
     * 获取控制数据
     */
    ControlData getData();
    
    /**
     * 更新控制数据
     */
    void updateData(ControlData data);
}

