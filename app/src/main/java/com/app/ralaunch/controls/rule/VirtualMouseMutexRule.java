package com.app.ralaunch.controls.rule;

import android.view.MotionEvent;

/**
 * 虚拟鼠标互斥规则
 * 虚拟鼠标与虚拟控件互斥，不能同时启用
 */
public class VirtualMouseMutexRule implements ControlRule {
    private static final int PRIORITY = 90;

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean isApplicable(MotionEvent event, ControlContext context) {
        // 只有在涉及虚拟鼠标时才适用
        return true;
    }

    @Override
    public RuleDecision evaluate(int pointerId, ControlType controlType, ControlContext context) {
        // 如果请求虚拟鼠标，但虚拟控件正在使用，拒绝
        if (controlType == ControlType.VIRTUAL_MOUSE) {
            if (context.isVirtualControlsEnabled() && !context.isVirtualMouseEnabled()) {
                // 虚拟控件优先
                return RuleDecision.DENY;
            }
        }

        // 如果请求虚拟控件，但虚拟鼠标正在使用，允许（虚拟控件优先级更高）
        if (controlType == ControlType.VIRTUAL_BUTTON || controlType == ControlType.VIRTUAL_JOYSTICK) {
            if (context.isVirtualMouseEnabled()) {
                // 应该先禁用虚拟鼠标
                return RuleDecision.ALLOW;
            }
        }

        return RuleDecision.SKIP;
    }
}
