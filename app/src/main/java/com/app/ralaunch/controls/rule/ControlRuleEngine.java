package com.app.ralaunch.controls.rule;

import android.view.MotionEvent;

/**
 * 控制规则引擎接口
 * 负责统一管理虚拟鼠标、虚拟控件、摇杆之间的交互规则，确保它们互不干扰
 */
public interface ControlRuleEngine {

    /**
     * 处理触摸事件，根据规则决定如何分发
     * @param event 触摸事件
     * @param context 控制上下文信息
     * @return 是否消费该事件
     */
    boolean processTouchEvent(MotionEvent event, ControlContext context);

    /**
     * 判断触摸点是否应该被某个控件处理
     * @param pointerId 触摸点ID
     * @param controlType 控件类型
     * @return 是否允许处理
     */
    boolean canHandleTouch(int pointerId, ControlType controlType);

    /**
     * 注册触摸点被某个控件占用
     * @param pointerId 触摸点ID
     * @param controlType 占用的控件类型
     */
    void registerTouchOwner(int pointerId, ControlType controlType);

    /**
     * 释放触摸点占用
     * @param pointerId 触摸点ID
     */
    void releaseTouchOwner(int pointerId);

    /**
     * 检查虚拟控件是否应该响应
     * @param controlId 控件ID
     * @param context 控制上下文
     * @return 是否允许响应
     */
    boolean shouldEnableVirtualControl(String controlId, ControlContext context);

    /**
     * 添加规则
     * @param rule 控制规则
     */
    void addRule(ControlRule rule);

    /**
     * 移除规则
     * @param rule 控制规则
     */
    void removeRule(ControlRule rule);

    /**
     * 清空所有规则
     */
    void clearRules();

    /**
     * 重置引擎状态
     */
    void reset();
}
