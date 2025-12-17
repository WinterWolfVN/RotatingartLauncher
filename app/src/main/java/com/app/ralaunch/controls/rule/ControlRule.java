package com.app.ralaunch.controls.rule;

import android.view.MotionEvent;

/**
 * 控制规则接口
 * 每个规则负责特定的控制逻辑判断
 */
public interface ControlRule {

    /**
     * 规则优先级（数值越大优先级越高）
     */
    int getPriority();

    /**
     * 检查规则是否适用于当前上下文
     * @param event 触摸事件
     * @param context 控制上下文
     * @return 是否适用
     */
    boolean isApplicable(MotionEvent event, ControlContext context);

    /**
     * 执行规则判断
     * @param pointerId 触摸点ID
     * @param controlType 请求处理的控件类型
     * @param context 控制上��文
     * @return 判断结果
     */
    RuleDecision evaluate(int pointerId, ControlType controlType, ControlContext context);

    /**
     * 规则判断结果
     */
    enum RuleDecision {
        /** 允许 */
        ALLOW,
        /** 拒绝 */
        DENY,
        /** 跳过（由其他规则决定） */
        SKIP
    }
}
