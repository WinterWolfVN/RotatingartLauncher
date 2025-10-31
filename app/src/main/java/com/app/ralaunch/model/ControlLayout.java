package com.app.ralaunch.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 控制布局数据模型
 * 
 * 表示一个完整的游戏控制布局，包含：
 * - 布局名称
 * - 控制元素列表（按钮、摇杆、十字键等）
 * 
 * 用于保存和加载游戏的自定义控制方案
 */
public class ControlLayout {
    private String name;
    private List<ControlElement> elements;

    public ControlLayout(String name) {
        this.name = name;
        this.elements = new ArrayList<>();
    }

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<ControlElement> getElements() { return elements; }
    public void setElements(List<ControlElement> elements) { this.elements = elements; }

    public void addElement(ControlElement element) {
        elements.add(element);
    }

    public void removeElement(ControlElement element) {
        elements.remove(element);
    }
}