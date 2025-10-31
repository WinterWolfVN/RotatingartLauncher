package com.app.ralaunch.model;

/**
 * 组件安装项数据模型
 * 
 * 表示一个需要安装的组件，包含：
 * - 组件名称和描述
 * - 安装包文件名
 * - 安装状态和进度
 * 
 * 用于在初始化过程中跟踪组件安装状态
 */
public class ComponentItem {
    private String name;
    private String description;
    private String fileName;
    private boolean installed;
    private int progress;

    public ComponentItem(String name, String description, String fileName) {
        this.name = name;
        this.description = description;
        this.fileName = fileName;
        this.installed = false;
        this.progress = 0;
    }

    // Getters and Setters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getFileName() { return fileName; }
    public boolean isInstalled() { return installed; }
    public void setInstalled(boolean installed) { this.installed = installed; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
}