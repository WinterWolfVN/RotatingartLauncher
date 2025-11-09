package com.app.ralaunch.controls;

import androidx.annotation.Keep;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 控制布局配置
 * 支持JSON序列化/反序列化
 */
@Keep
public class ControlConfig {
    @SerializedName("name")
    public String name;
    
    @SerializedName("version")
    public int version;
    
    @SerializedName("controls")
    public List<ControlData> controls;
    
    public ControlConfig() {
        this.name = "Custom Layout";
        this.version = 1;
        this.controls = new ArrayList<>();
    }
    
    /**
     * 从JSON文件加载配置
     */
    public static ControlConfig loadFromFile(File file) throws IOException {
        if (!file.exists()) {
            throw new IOException("Config file not found: " + file.getPath());
        }
        
        try (FileReader reader = new FileReader(file)) {
            Gson gson = new Gson();
            return gson.fromJson(reader, ControlConfig.class);
        }
    }
    
    /**
     * 从InputStream加载配置
     */
    public static ControlConfig loadFromStream(InputStream stream) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(stream)) {
            Gson gson = new Gson();
            return gson.fromJson(reader, ControlConfig.class);
        }
    }
    
    /**
     * 从JSON字符串加载配置
     */
    public static ControlConfig loadFromJson(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, ControlConfig.class);
    }
    
    /**
     * 保存配置到JSON文件
     */
    public void saveToFile(File file) throws IOException {
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        try (FileWriter writer = new FileWriter(file)) {
            Gson gson = new Gson();
            gson.toJson(this, writer);
        }
    }
    
    /**
     * 转换为JSON字符串
     */
    public String toJson() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}
