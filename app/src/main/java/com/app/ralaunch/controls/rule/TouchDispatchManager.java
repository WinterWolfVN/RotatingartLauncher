package com.app.ralaunch.controls.rule;

import android.util.Log;
import android.view.MotionEvent;

/**
 * 触摸事件分发管理器
 * 统一管理虚拟控件和游戏原生触摸之间的事件分发
 * 确保虚拟鼠标、虚拟控件、游戏触摸互不干扰
 */
public class TouchDispatchManager {
    private static final String TAG = "TouchDispatchManager";

    /** 单例实例 */
    private static TouchDispatchManager sInstance;

    /** 控制规则引擎 */
    private final ControlRuleEngine mRuleEngine;

    /** 控制上下文 */
    private final ControlContext mContext;

    /** 是否启用调试日志 */
    private boolean mDebugEnabled = false;

    private TouchDispatchManager() {
        mRuleEngine = new DefaultControlRuleEngine();
        mContext = new ControlContext();
    }

    /**
     * 获取单例实例
     */
    public static synchronized TouchDispatchManager getInstance() {
        if (sInstance == null) {
            sInstance = new TouchDispatchManager();
        }
        return sInstance;
    }

    /**
     * 初始化管理器
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     */
    public void initialize(int screenWidth, int screenHeight) {
        mContext.setScreenWidth(screenWidth);
        mContext.setScreenHeight(screenHeight);
        logDebug("Initialized with screen size: " + screenWidth + "x" + screenHeight);
    }

    /**
     * 请求处理触摸点
     * @param pointerId 触摸点ID
     * @param controlType 控件类型
     * @return 是否允许处理
     */
    public boolean requestTouchHandle(int pointerId, ControlType controlType) {
        boolean canHandle = mRuleEngine.canHandleTouch(pointerId, controlType);
        if (canHandle) {
            mRuleEngine.registerTouchOwner(pointerId, controlType);
            logDebug("Touch " + pointerId + " granted to " + controlType);
        } else {
            logDebug("Touch " + pointerId + " denied for " + controlType);
        }
        return canHandle;
    }

    /**
     * 释放触摸点占用
     * @param pointerId 触摸点ID
     */
    public void releaseTouchHandle(int pointerId) {
        mRuleEngine.releaseTouchOwner(pointerId);
        logDebug("Touch " + pointerId + " released");
    }

    /**
     * 检查触摸点是否被占用
     * @param pointerId 触摸点ID
     * @param controlType 控件类型
     * @return 是否被占用
     */
    public boolean isTouchOccupied(int pointerId, ControlType controlType) {
        return !mRuleEngine.canHandleTouch(pointerId, controlType);
    }


    /**
     * 请求启用虚拟控件
     * @param controlId 控件ID
     * @return 是否允许启用
     */
    public boolean requestEnableVirtualControl(String controlId) {
        boolean allowed = mRuleEngine.shouldEnableVirtualControl(controlId, mContext);
        if (allowed) {
            mContext.setVirtualControlsEnabled(true);
            mContext.setActiveControlId(controlId);
            logDebug("Virtual control enabled: " + controlId);
        } else {
            logDebug("Virtual control denied: " + controlId);
        }
        return allowed;
    }

    /**
     * 禁用虚拟控件
     */
    public void disableVirtualControl() {
        mContext.setVirtualControlsEnabled(false);
        mContext.setActiveControlId(null);
        logDebug("Virtual control disabled");
    }

    /**
     * 处理触摸事件
     * @param event 触摸事件
     * @return 是否消费该事件
     */
    public boolean dispatchTouchEvent(MotionEvent event) {
        mContext.setCurrentEvent(event);
        return mRuleEngine.processTouchEvent(event, mContext);
    }

    /**
     * 添加自定义规则
     * @param rule 控制规则
     */
    public void addRule(ControlRule rule) {
        mRuleEngine.addRule(rule);
        logDebug("Added rule: " + rule.getClass().getSimpleName());
    }

    /**
     * 移除规则
     * @param rule 控制规则
     */
    public void removeRule(ControlRule rule) {
        mRuleEngine.removeRule(rule);
        logDebug("Removed rule: " + rule.getClass().getSimpleName());
    }

    /**
     * 清空所有自定义规则（保留默认规则）
     */
    public void clearCustomRules() {
        // 注意：这会清除所有规则，包括默认规则
        // 如果需要保留默认规则，需要在 ControlRuleEngine 中实现区分
        mRuleEngine.clearRules();
        logDebug("Cleared all custom rules");
    }

    /**
     * 重置管理器状态
     */
    public void reset() {
        mRuleEngine.reset();
        mContext.setVirtualControlsEnabled(false);
        mContext.setActiveControlId(null);
        logDebug("TouchDispatchManager reset");
    }

    /**
     * 获取控制规则引擎（用于高级自定义）
     */
    public ControlRuleEngine getRuleEngine() {
        return mRuleEngine;
    }

    /**
     * 获取控制上下文
     */
    public ControlContext getContext() {
        return mContext;
    }

    /**
     * 设置是否启用调试日志
     */
    public void setDebugEnabled(boolean enabled) {
        mDebugEnabled = enabled;
    }

    private void logDebug(String message) {
        if (mDebugEnabled) {
            Log.d(TAG, message);
        }
    }

    /**
     * 获取虚拟控件状态
     */
    public boolean isVirtualControlsEnabled() {
        return mContext.isVirtualControlsEnabled();
    }

    /**
     * 获取当前激活的控件ID
     */
    public String getActiveControlId() {
        return mContext.getActiveControlId();
    }

    /**
     * 设置虚拟鼠标移动范围
     * @param left 左侧范围 (0.0-1.0, 从屏幕中心向左扩展的百分比)
     * @param top 顶部范围 (0.0-1.0, 从屏幕中心向上扩展的百分比)
     * @param right 右侧范围 (0.0-1.0, 从屏幕中心向右扩展的百分比)
     * @param bottom 底部范围 (0.0-1.0, 从屏幕中心向下扩展的百分比)
     */
    public void setMouseRange(float left, float top, float right, float bottom) {
        mContext.setMouseRange(left, top, right, bottom);
        logDebug("Mouse range set: L=" + left + " T=" + top + " R=" + right + " B=" + bottom);
    }

    /**
     * 添加虚拟控件占用区域
     * @param left 左边界(像素)
     * @param top 顶边界(像素)
     * @param right 右边界(像素)
     * @param bottom 底边界(像素)
     */
    public void addVirtualControlArea(float left, float top, float right, float bottom) {
        mContext.addVirtualControlArea(left, top, right, bottom);
        logDebug("Added virtual control area: (" + left + "," + top + ")-(" + right + "," + bottom + ")");
    }

    /**
     * 清空虚拟控件区域
     */
    public void clearVirtualControlAreas() {
        mContext.clearVirtualControlAreas();
        logDebug("Cleared virtual control areas");
    }

    /**
     * 检查点是否在虚拟控件区域内
     */
    public boolean isPointInVirtualControlArea(float x, float y) {
        return mContext.isPointInVirtualControlArea(x, y);
    }

}
