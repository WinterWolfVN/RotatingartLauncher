package com.app.ralaunch.controls.rule;

import android.view.MotionEvent;

/**
 * 用户设置优先规则
 * 确保用户在设置中的偏好被优先执行
 *
 * 例如:
 * - 如果用户在设置中禁用了虚拟鼠标,则无论什么情况都不允许启用虚拟鼠标
 * - 这确保了用户设置的最高优先级
 */
public class UserPreferenceRule implements ControlRule {
    private static final int PRIORITY = 200; // 最高优先级,高于所有其他规则

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean isApplicable(MotionEvent event, ControlContext context) {
        // 只在涉及虚拟鼠标时适用
        return true;
    }

    @Override
    public RuleDecision evaluate(int pointerId, ControlType controlType, ControlContext context) {
        // 如果请求启用虚拟鼠标,检查用户是否禁用了它
        if (controlType == ControlType.VIRTUAL_MOUSE) {
            if (context.isUserDisabledVirtualMouse()) {
                // 用户明确禁用了虚拟鼠标,拒绝
                return RuleDecision.DENY;
            }
        }

        // 其他情况跳过,由其他规则决定
        return RuleDecision.SKIP;
    }
}
