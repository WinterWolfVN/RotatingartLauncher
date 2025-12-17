package com.app.ralaunch.controls.rule;

import android.view.MotionEvent;

/**
 * 控制上下文信息
 * 包含处理触摸事件所需的所有上下文数据
 */
public class ControlContext {
    /** 当前触摸事件 */
    private MotionEvent currentEvent;

    /** 屏幕宽度 */
    private int screenWidth;

    /** 屏幕高度 */
    private int screenHeight;

    /** 是否启用虚拟鼠标 */
    private boolean virtualMouseEnabled;

    /** 是否启用虚拟控件 */
    private boolean virtualControlsEnabled;

    /** 当前激活的控件ID */
    private String activeControlId;

    /** 虚拟鼠标移动范围 (0.0-1.0, 屏幕中心向外扩展的百分比) */
    private float mouseRangeLeft = 1.0f;
    private float mouseRangeTop = 1.0f;
    private float mouseRangeRight = 1.0f;
    private float mouseRangeBottom = 1.0f;

    /** 虚拟控件占用的屏幕区域列表 */
    private java.util.List<android.graphics.RectF> virtualControlAreas = new java.util.ArrayList<>();

    /** 用户是否在设置中禁用了虚拟鼠标 */
    private boolean userDisabledVirtualMouse = false;

    public ControlContext() {
    }

    public MotionEvent getCurrentEvent() {
        return currentEvent;
    }

    public void setCurrentEvent(MotionEvent currentEvent) {
        this.currentEvent = currentEvent;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public void setScreenWidth(int screenWidth) {
        this.screenWidth = screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public void setScreenHeight(int screenHeight) {
        this.screenHeight = screenHeight;
    }

    public boolean isVirtualMouseEnabled() {
        return virtualMouseEnabled;
    }

    public void setVirtualMouseEnabled(boolean virtualMouseEnabled) {
        this.virtualMouseEnabled = virtualMouseEnabled;
    }

    public boolean isVirtualControlsEnabled() {
        return virtualControlsEnabled;
    }

    public void setVirtualControlsEnabled(boolean virtualControlsEnabled) {
        this.virtualControlsEnabled = virtualControlsEnabled;
    }

    public String getActiveControlId() {
        return activeControlId;
    }

    public void setActiveControlId(String activeControlId) {
        this.activeControlId = activeControlId;
    }

    public float getMouseRangeLeft() {
        return mouseRangeLeft;
    }

    public void setMouseRangeLeft(float mouseRangeLeft) {
        this.mouseRangeLeft = mouseRangeLeft;
    }

    public float getMouseRangeTop() {
        return mouseRangeTop;
    }

    public void setMouseRangeTop(float mouseRangeTop) {
        this.mouseRangeTop = mouseRangeTop;
    }

    public float getMouseRangeRight() {
        return mouseRangeRight;
    }

    public void setMouseRangeRight(float mouseRangeRight) {
        this.mouseRangeRight = mouseRangeRight;
    }

    public float getMouseRangeBottom() {
        return mouseRangeBottom;
    }

    public void setMouseRangeBottom(float mouseRangeBottom) {
        this.mouseRangeBottom = mouseRangeBottom;
    }

    /**
     * 设置虚拟鼠标移动范围
     */
    public void setMouseRange(float left, float top, float right, float bottom) {
        this.mouseRangeLeft = left;
        this.mouseRangeTop = top;
        this.mouseRangeRight = right;
        this.mouseRangeBottom = bottom;
    }

    public java.util.List<android.graphics.RectF> getVirtualControlAreas() {
        return virtualControlAreas;
    }

    /**
     * 添加虚拟控件占用区域
     */
    public void addVirtualControlArea(float left, float top, float right, float bottom) {
        virtualControlAreas.add(new android.graphics.RectF(left, top, right, bottom));
    }

    /**
     * 清空虚拟控件区域
     */
    public void clearVirtualControlAreas() {
        virtualControlAreas.clear();
    }

    /**
     * 检查点是否在虚拟控件区域内
     */
    public boolean isPointInVirtualControlArea(float x, float y) {
        for (android.graphics.RectF rect : virtualControlAreas) {
            if (rect.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    public boolean isUserDisabledVirtualMouse() {
        return userDisabledVirtualMouse;
    }

    public void setUserDisabledVirtualMouse(boolean userDisabledVirtualMouse) {
        this.userDisabledVirtualMouse = userDisabledVirtualMouse;
    }

    /**
     * 计算虚拟鼠标的实际移动范围(像素值)
     */
    public android.graphics.RectF calculateMouseRangeBounds() {
        float centerX = screenWidth * 0.5f;
        float centerY = screenHeight * 0.5f;

        float minX = centerX - (mouseRangeLeft * centerX);
        float maxX = centerX + (mouseRangeRight * centerX);
        float minY = centerY - (mouseRangeTop * centerY);
        float maxY = centerY + (mouseRangeBottom * centerY);

        return new android.graphics.RectF(minX, minY, maxX, maxY);
    }

    /**
     * 创建上下文副本
     */
    public ControlContext copy() {
        ControlContext context = new ControlContext();
        context.currentEvent = this.currentEvent;
        context.screenWidth = this.screenWidth;
        context.screenHeight = this.screenHeight;
        context.virtualMouseEnabled = this.virtualMouseEnabled;
        context.virtualControlsEnabled = this.virtualControlsEnabled;
        context.activeControlId = this.activeControlId;
        context.mouseRangeLeft = this.mouseRangeLeft;
        context.mouseRangeTop = this.mouseRangeTop;
        context.mouseRangeRight = this.mouseRangeRight;
        context.mouseRangeBottom = this.mouseRangeBottom;
        context.virtualControlAreas = new java.util.ArrayList<>(this.virtualControlAreas);
        context.userDisabledVirtualMouse = this.userDisabledVirtualMouse;
        return context;
    }
}
