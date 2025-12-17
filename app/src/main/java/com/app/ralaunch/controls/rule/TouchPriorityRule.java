package com.app.ralaunch.controls.rule;

import android.view.MotionEvent;

/**
 * 触摸点优先级规则
 * 基于控件类型的优先级决定触摸点归属
 * 优先级顺序：虚拟按钮 > 虚拟摇杆 > 虚拟鼠标 > 游戏原生触摸
 */
public class TouchPriorityRule implements ControlRule {
    private static final int PRIORITY = 80;

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean isApplicable(MotionEvent event, ControlContext context) {
        // 此规则始终适用
        return true;
    }

    @Override
    public RuleDecision evaluate(int pointerId, ControlType controlType, ControlContext context) {
        // 优先级判断已在 DefaultControlRuleEngine.canHandleTouch() 中实现
        // 这里只是声明规则存在
        return RuleDecision.SKIP;
    }
}
