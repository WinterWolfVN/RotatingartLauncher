package com.app.ralaunch.renderer;

import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

/**
 * DXVK 渲染器加载器
 * 
 * DXVK 是一个将 Direct3D 8/9/10/11 翻译为 Vulkan 的层
 * 用于在 Android 上运行使用 Direct3D 的应用程序
 */
public class DXVKLoader {
    private static final String TAG = "DXVKLoader";
    
    // DXVK 组件
    public static final String COMPONENT_DXGI = "dxgi";
    public static final String COMPONENT_D3D8 = "d3d8";
    public static final String COMPONENT_D3D9 = "d3d9";
    public static final String COMPONENT_D3D10 = "d3d10";
    public static final String COMPONENT_D3D11 = "d3d11";
    
    private static boolean sInitialized = false;
    private static boolean sAvailable = false;
    
    static {
        try {
            System.loadLibrary("main");
            sAvailable = nativeIsAvailable();
            Log.i(TAG, "DXVK available: " + sAvailable);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library", e);
            sAvailable = false;
        }
    }
    
    /**
     * 初始化 DXVK 渲染器
     * 
     * @return true 成功
     */
    public static boolean init() {
        if (sInitialized) {
            Log.i(TAG, "DXVK already initialized");
            return true;
        }
        
        if (!sAvailable) {
            Log.e(TAG, "DXVK not available");
            return false;
        }
        
        Log.i(TAG, "Initializing DXVK...");
        sInitialized = nativeInit();
        
        if (sInitialized) {
            Log.i(TAG, "DXVK initialized successfully, version: " + getVersion());
        } else {
            Log.e(TAG, "Failed to initialize DXVK");
        }
        
        return sInitialized;
    }
    
    /**
     * 检查 DXVK 是否可用
     * 
     * @return true DXVK 库存在
     */
    public static boolean isAvailable() {
        return sAvailable;
    }
    
    /**
     * 检查 DXVK 是否已初始化
     * 
     * @return true 已初始化
     */
    public static boolean isInitialized() {
        return sInitialized;
    }
    
    /**
     * 获取 DXVK 版本
     * 
     * @return 版本字符串
     */
    public static String getVersion() {
        if (!sAvailable) {
            return "N/A";
        }
        return nativeGetVersion();
    }
    
    /**
     * 加载指定的 DXVK 组件
     * 
     * @param component 组件名称 (COMPONENT_D3D8, COMPONENT_D3D9, 等)
     * @return true 成功
     */
    public static boolean loadComponent(String component) {
        if (!sInitialized) {
            Log.e(TAG, "DXVK not initialized, call init() first");
            return false;
        }
        
        Log.i(TAG, "Loading DXVK component: " + component);
        return nativeLoadComponent(component);
    }
    
    /**
     * 加载 Direct3D 9 支持
     * 
     * @return true 成功
     */
    public static boolean loadD3D9() {
        return loadComponent(COMPONENT_D3D9);
    }
    
    /**
     * 加载 Direct3D 11 支持
     * 
     * @return true 成功
     */
    public static boolean loadD3D11() {
        return loadComponent(COMPONENT_D3D11);
    }
    
    /**
     * 清理 DXVK 资源
     */
    public static void cleanup() {
        if (sInitialized) {
            Log.i(TAG, "Cleaning up DXVK...");
            nativeCleanup();
            sInitialized = false;
        }
    }
    
    /**
     * 设置 DXVK 环境变量
     * 
     * @param key 变量名
     * @param value 值
     */
    public static void setEnv(String key, String value) {
        try {
            Os.setenv(key, value, true);
        } catch (ErrnoException e) {
            Log.e(TAG, "Failed to set env " + key + ": " + e.getMessage());
        }
    }
    
    /**
     * 启用 DXVK HUD 显示
     * 
     * @param options HUD 选项，如 "fps,version,devinfo"
     */
    public static void enableHUD(String options) {
        setEnv("DXVK_HUD", options);
    }
    
    /**
     * 禁用 DXVK HUD
     */
    public static void disableHUD() {
        setEnv("DXVK_HUD", "0");
    }
    
    /**
     * 设置 DXVK 日志级别
     * 
     * @param level 日志级别: none, error, warn, info, debug
     */
    public static void setLogLevel(String level) {
        setEnv("DXVK_LOG_LEVEL", level);
    }
    
    // Native 方法
    private static native boolean nativeInit();
    private static native boolean nativeIsAvailable();
    private static native String nativeGetVersion();
    private static native boolean nativeLoadComponent(String component);
    private static native void nativeCleanup();
}

