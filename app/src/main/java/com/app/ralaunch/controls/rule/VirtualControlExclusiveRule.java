package com.app.ralaunch.controls.rule;

import android.view.MotionEvent;

/**
 * 虚拟控件独占规则
 * 当虚拟控件（按钮、摇杆）占用触摸点后，其他类型不能抢占
 */
public class VirtualControlExclusiveRule implements ControlRule {
    private static final int PRIORITY = 100; // 最高优先级

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
        // 此规则需要在 DefaultControlRuleEngine 中通过 canHandleTouch 实现
        // 这里主要用于声明规则逻辑
        return RuleDecision.SKIP;
    }
}
