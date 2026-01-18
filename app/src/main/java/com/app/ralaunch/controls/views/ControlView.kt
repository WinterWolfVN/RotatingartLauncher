package com.app.ralaunch.controls.views

import com.app.ralaunch.controls.data.ControlData
import java.io.File

/**
 * 虚拟控制View接口
 * 所有虚拟控制元素（按钮、摇杆等）都实现此接口
 */
interface ControlView {
    /**
     * 控制数据
     */
    var controlData: ControlData
    
    /**
     * 设置控件包资源目录（用于加载纹理）
     * 默认实现为空，子类可以覆盖以支持纹理
     */
    fun setPackAssetsDir(dir: File?) {
        // 默认不做任何操作
    }
    
    /**
     * 检查触摸点是否在控件的实际形状内（考虑圆形、矩形等不同形状）
     * @param x 触摸点的X坐标（相对于父视图）
     * @param y 触摸点的Y坐标（相对于父视图）
     * @return true 如果触摸点在控件形状内，false 否则
     */
    fun isTouchInBounds(x: Float, y: Float): Boolean
}