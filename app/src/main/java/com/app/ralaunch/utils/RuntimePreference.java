package com.app.ralaunch.utils;

import android.content.Context;

/**
 * 运行时首选项 - 简化版
 * 直接使用默认安装的 .NET 运行时
 */
public final class RuntimePreference {
    private static final String TAG = "RuntimePreference";

    private RuntimePreference() {}

    /**
     * 获取 .NET 运行时根路径
     */
    public static String getDotnetRootPath() {
        try {
            Context appContext = com.app.ralaunch.RaLaunchApplication.getAppContext();
            if (appContext == null) {
                android.util.Log.w(TAG, "Application context is null, cannot get dotnet root path");
                return null;
            }
            
            String dotnetDir = "dotnet";
            String dotnetPath = appContext.getFilesDir().getAbsolutePath() + "/" + dotnetDir;
            android.util.Log.d(TAG, "Dotnet root path: " + dotnetPath);
            
            return dotnetPath;
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to get dotnet root path", e);
            return null;
        }
    }

    public static void setVerboseLogging(Context context, boolean enabled) {
        com.app.ralaunch.data.SettingsManager.getInstance().setVerboseLogging(enabled);
    }

    public static boolean isVerboseLogging(Context context) {
        return com.app.ralaunch.data.SettingsManager.getInstance().isVerboseLogging();
    }
}
