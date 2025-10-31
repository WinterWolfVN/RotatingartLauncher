package com.app.ralaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 运行时框架偏好设置管理
 * 
 * 管理 .NET Framework 版本偏好设置：
 * - 保存和读取用户选择的框架版本（net6/net7/net8/net9/net10/auto）
 * - 保存和读取用户选择的 CPU 架构（arm64/x86_64/auto）
 * - 提供统一的偏好存取接口
 * 
 * 注意：此类主要用于兼容旧的框架版本选择方式，
 * 新的运行时管理推荐使用 RuntimeManager
 */
public final class RuntimePreference {
    private static final String PREFS = "app_prefs";
    private static final String KEY_DOTNET = "dotnet_framework";
    private static final String KEY_ARCHITECTURE = "runtime_architecture";
    
    // CPU 架构常量
    public static final String ARCH_ARM64 = "arm64";
    public static final String ARCH_X86_64 = "x86_64";
    public static final String ARCH_AUTO = "auto";

    private RuntimePreference() {}

    /**
     * 设置 .NET Framework 版本偏好
     * 
     * @param context Android 上下文
     * @param value 框架版本（net6/net7/net8/net9/net10/auto）
     */
    public static void setDotnetFramework(Context context, String value) {
        if (value == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_DOTNET, value)
                .apply();
    }

    /**
     * 获取 .NET Framework 版本偏好
     * 
     * @param context Android 上下文
     * @return 框架版本，默认为 "auto"
     */
    public static String getDotnetFramework(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getString(KEY_DOTNET, "auto");
    }

    /**
     * 设置运行时 CPU 架构偏好
     * 
     * @param context Android 上下文
     * @param architecture 架构（arm64/x86_64/auto）
     */
    public static void setArchitecture(Context context, String architecture) {
        if (architecture == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ARCHITECTURE, architecture)
                .apply();
    }

    /**
     * 获取运行时 CPU 架构偏好
     * 
     * @param context Android 上下文
     * @return 架构，默认为 "arm64"
     */
    public static String getArchitecture(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getString(KEY_ARCHITECTURE, ARCH_ARM64); // 默认 ARM64
    }

    /**
     * 获取当前设备的 CPU 架构
     * 
     * @return arm64 或 x86_64
     */
    public static String getDeviceArchitecture() {
        String[] abis = android.os.Build.SUPPORTED_ABIS;
        if (abis != null && abis.length > 0) {
            String primaryAbi = abis[0];
            if (primaryAbi.startsWith("arm64") || primaryAbi.startsWith("armeabi")) {
                return ARCH_ARM64;
            } else if (primaryAbi.startsWith("x86_64")) {
                return ARCH_X86_64;
            }
        }
        return ARCH_ARM64; // 默认返回 ARM64
    }

    /**
     * 获取实际应该使用的架构（考虑 auto 模式）
     * 
     * @param context Android 上下文
     * @return 实际的架构（arm64 或 x86_64）
     */
    public static String getEffectiveArchitecture(Context context) {
        String arch = getArchitecture(context);
        if (ARCH_AUTO.equals(arch)) {
            return getDeviceArchitecture();
        }
        return arch;
    }
}


