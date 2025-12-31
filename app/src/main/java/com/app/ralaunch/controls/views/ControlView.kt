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
}