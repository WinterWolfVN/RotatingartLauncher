package com.app.ralaunch.utils;

import android.content.Context;
import com.app.ralaunch.utils.AppLogger;

import java.io.File;

/**
 * SteamCMD 检查工具
 * 
 * 注意：SteamCMD 在初始化阶段已解压，此类仅用于检查 SteamCMD 是否存在
 */
public class SteamCMDExtractor {
    private static final String TAG = "SteamCMDExtractor";
    private static final String STEAMCMD_BINARY = "linux32/steamcmd";
    
    /**
     * 检查 SteamCMD 是否已解压（初始化阶段已解压）
     * 
     * @param context Android 上下文
     * @return true 已存在，false 不存在
     */
    public static boolean extractSteamCMDIfNeeded(Context context) {
        File steamcmdDir = new File(context.getFilesDir(), "steamcmd");
        File steamcmdBinary = new File(steamcmdDir, STEAMCMD_BINARY);
        
        // 检查二进制文件是否存在
        if (steamcmdBinary.exists() && steamcmdBinary.canRead()) {
            AppLogger.info(TAG, "SteamCMD binary found: " + steamcmdBinary.getAbsolutePath());
            return true;
        }
        
        // 也检查 steamcmd.sh（备用）
        File steamcmdScript = new File(steamcmdDir, "steamcmd.sh");
        if (steamcmdScript.exists() && steamcmdScript.canRead()) {
            AppLogger.info(TAG, "SteamCMD script found: " + steamcmdScript.getAbsolutePath());
            return true;
        }
        
        AppLogger.warn(TAG, "SteamCMD not found at: " + steamcmdBinary.getAbsolutePath());
        AppLogger.warn(TAG, "SteamCMD directory exists: " + steamcmdDir.exists());
        if (steamcmdDir.exists()) {
            File[] files = steamcmdDir.listFiles();
            if (files != null) {
                AppLogger.warn(TAG, "Files in steamcmd directory: " + java.util.Arrays.toString(
                    java.util.Arrays.stream(files).map(File::getName).toArray(String[]::new)));
            }
        }
        AppLogger.warn(TAG, "Please run initialization first.");
        return false;
    }
    
    /**
     * 获取 SteamCMD 目录路径
     * 
     * @param context Android 上下文
     * @return SteamCMD 目录路径
     */
    public static String getSteamCMDDir(Context context) {
        return new File(context.getFilesDir(), "steamcmd").getAbsolutePath();
    }
}

