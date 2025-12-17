package com.app.ralaunch.controls.rule;

import android.graphics.RectF;
import android.view.MotionEvent;

/**
 * 鼠标区域冲突检测规则
 * 确保虚拟鼠标的移动范围不会与虚拟控件的位置冲突
 *
 * 工作原理:
 * 1. 根据设置的鼠标移动范围(屏幕中心向外扩展的百分比)计算实际区域
 * 2. 检测该区域是否与虚拟控件(按钮、摇杆)的位置重叠
 * 3. 如果重叠,则拒绝启用虚拟鼠标,或调整鼠标移动范围
 */
public class MouseRangeConflictRule implements ControlRule {
    private static final int PRIORITY = 85;

    /** 允许的最小重叠面积百分比(0.0-1.0) */
    private float mAllowedOverlapRatio = 0.1f; // 10%

    public MouseRangeConflictRule() {
    }

    public MouseRangeConflictRule(float allowedOverlapRatio) {
        this.mAllowedOverlapRatio = allowedOverlapRatio;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean isApplicable(MotionEvent event, ControlContext context) {
        // 只在虚拟鼠标相关操作时适用
        return context.isVirtualMouseEnabled() ||
               !context.getVirtualControlAreas().isEmpty();
    }

    @Override
    public RuleDecision evaluate(int pointerId, ControlType controlType, ControlContext context) {
        // 只处理虚拟鼠标类型
        if (controlType != ControlType.VIRTUAL_MOUSE) {
            return RuleDecision.SKIP;
        }

        // 如果没有虚拟控件区域,允许
        if (context.getVirtualControlAreas().isEmpty()) {
            return RuleDecision.ALLOW;
        }

        // 计算虚拟鼠标的移动范围
        RectF mouseRange = context.calculateMouseRangeBounds();

        // 检查是否与虚拟控件区域冲突
        float totalOverlapArea = 0f;
        float mouseRangeArea = mouseRange.width() * mouseRange.height();

        for (RectF controlArea : context.getVirtualControlAreas()) {
            if (RectF.intersects(mouseRange, controlArea)) {
                // 计算重叠区域
                RectF intersection = new RectF();
                if (intersection.setIntersect(mouseRange, controlArea)) {
                    float overlapArea = intersection.width() * intersection.height();
                    totalOverlapArea += overlapArea;
                }
            }
        }

        // 计算重叠比例
        float overlapRatio = mouseRangeArea > 0 ? (totalOverlapArea / mouseRangeArea) : 0f;

        // 如果重叠比例超过阈值,拒绝
        if (overlapRatio > mAllowedOverlapRatio) {
            return RuleDecision.DENY;
        }

        return RuleDecision.ALLOW;
    }

    /**
     * 设置允许的最小重叠面积百分比
     */
    public void setAllowedOverlapRatio(float ratio) {
        this.mAllowedOverlapRatio = Math.max(0f, Math.min(1f, ratio));
    }

    /**
     * 获取允许的重叠比例
     */
    public float getAllowedOverlapRatio() {
        return mAllowedOverlapRatio;
    }
}
