package com.app.ralaunch.controls.rule;

import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 默认控制规则引擎实现
 * 基于优先级和规则链进行触摸事件分发
 */
public class DefaultControlRuleEngine implements ControlRuleEngine {
    private static final String TAG = "ControlRuleEngine";

    /** 触摸点占用映射表：pointerId -> ControlType */
    private final SparseArray<ControlType> mTouchOwners = new SparseArray<>();

    /** 规则列表（按优先级排序） */
    private final List<ControlRule> mRules = new ArrayList<>();

    /** 规则是否需要重新排序 */
    private boolean mNeedSort = false;

    public DefaultControlRuleEngine() {
        // 添加默认规则
        addDefaultRules();
    }

    /**
     * 添加默认规则集
     */
    private void addDefaultRules() {
        // 1. 虚拟控件独占规则（优先级 100）
        addRule(new VirtualControlExclusiveRule());

        // 4. 触摸点优先级规则（优先级 80）
        addRule(new TouchPriorityRule());
    }

    @Override
    public boolean processTouchEvent(MotionEvent event, ControlContext context) {
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                // 触摸按下时不做处理，等待控件注册
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                // 触摸释放时清理占用
                releaseTouchOwner(pointerId);
                break;

            case MotionEvent.ACTION_MOVE:
                // 移动事件不改变所有者
                break;
        }

        return false;
    }

    @Override
    public boolean canHandleTouch(int pointerId, ControlType controlType) {
        // 1. 检查触摸点是否已被占用
        ControlType owner = mTouchOwners.get(pointerId);
        if (owner != null) {
            // 如果是同一个控件类型，允许继续处理
            if (owner == controlType) {
                return true;
            }
            // 如果被其他控件占用，检查优先级
            if (controlType.getPriority() <= owner.getPriority()) {
                return false; // 优先级不够，拒绝
            }
        }

        // 2. 执行规则链判断
        ControlContext context = new ControlContext();
        sortRulesIfNeeded();

        for (ControlRule rule : mRules) {
            if (rule.isApplicable(null, context)) {
                ControlRule.RuleDecision decision = rule.evaluate(pointerId, controlType, context);
                if (decision == ControlRule.RuleDecision.DENY) {
                    return false;
                } else if (decision == ControlRule.RuleDecision.ALLOW) {
                    return true;
                }
                // SKIP 继续下一个规则
            }
        }

        // 默认允许
        return true;
    }

    @Override
    public void registerTouchOwner(int pointerId, ControlType controlType) {
        ControlType oldOwner = mTouchOwners.get(pointerId);
        if (oldOwner != null && oldOwner != controlType) {
            Log.d(TAG, "Touch " + pointerId + " ownership changed: " +
                    oldOwner + " -> " + controlType);
        }
        mTouchOwners.put(pointerId, controlType);
    }

    @Override
    public void releaseTouchOwner(int pointerId) {
        mTouchOwners.remove(pointerId);
    }

    @Override
    public boolean shouldEnableVirtualControl(String controlId, ControlContext context) {
        context.setActiveControlId(controlId);

        // 执行规则链判断
        sortRulesIfNeeded();
        for (ControlRule rule : mRules) {
            if (rule.isApplicable(null, context)) {
                ControlRule.RuleDecision decision = rule.evaluate(-1, ControlType.VIRTUAL_JOYSTICK, context);
                if (decision == ControlRule.RuleDecision.DENY) {
                    return false;
                } else if (decision == ControlRule.RuleDecision.ALLOW) {
                    return true;
                }
            }
        }

        return true;
    }

    @Override
    public void addRule(ControlRule rule) {
        if (rule == null) {
            return;
        }
        mRules.add(rule);
        mNeedSort = true;
    }

    @Override
    public void removeRule(ControlRule rule) {
        mRules.remove(rule);
    }

    @Override
    public void clearRules() {
        mRules.clear();
        mNeedSort = false;
    }

    @Override
    public void reset() {
        mTouchOwners.clear();
    }

    /**
     * 按优先级排序规则（降序，优先级高的在前）
     */
    private void sortRulesIfNeeded() {
        if (mNeedSort) {
            Collections.sort(mRules, new Comparator<ControlRule>() {
                @Override
                public int compare(ControlRule r1, ControlRule r2) {
                    return Integer.compare(r2.getPriority(), r1.getPriority());
                }
            });
            mNeedSort = false;
        }
    }

    /**
     * 获取触摸点所有者类型（用于调试）
     */
    public ControlType getTouchOwner(int pointerId) {
        return mTouchOwners.get(pointerId);
    }

    /**
     * 获取当前所有占用的触摸点数量
     */
    public int getOccupiedTouchCount() {
        return mTouchOwners.size();
    }
}
