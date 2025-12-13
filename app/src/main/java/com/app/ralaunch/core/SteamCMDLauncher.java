package com.app.ralaunch.core;

import android.content.Context;
import com.app.ralaunch.utils.AppLogger;

import java.io.File;

/**
 * SteamCMD 启动器
 * 
 * 通过 Box64 转译运行 SteamCMD（x86_64 Linux 程序）
 */
public class SteamCMDLauncher {
    private static final String TAG = "SteamCMDLauncher";
    
    // SteamCMD 默认路径（从 assets 解压到应用数据目录）
    // 注意：Box64 不能直接运行 shell 脚本，需要运行实际的二进制文件
    private static final String STEAMCMD_BINARY = "linux32/steamcmd";
    
    /**
     * 初始化 Box64（如果尚未初始化）
     * 
     * @param context Android 上下文
     * @return true 成功，false 失败
     */
    public static boolean ensureBox64Initialized(Context context) {
        try {
            // 首先解压 Box64 x64 库（如果需要）
            AppLogger.info(TAG, "Extracting Box64 x64 libraries if needed...");
            if (!com.app.ralaunch.utils.Box64LibExtractor.extractLibsIfNeeded(context)) {
                AppLogger.warn(TAG, "Failed to extract Box64 x64 libraries, continuing anyway...");
            }
            
            // 解压 SteamCMD（如果需要）
            AppLogger.info(TAG, "Extracting SteamCMD if needed...");
            if (!com.app.ralaunch.utils.SteamCMDExtractor.extractSteamCMDIfNeeded(context)) {
                AppLogger.warn(TAG, "Failed to extract SteamCMD, continuing anyway...");
            }
            
            String dataDir = context.getFilesDir().getAbsolutePath();
            String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
            
            AppLogger.info(TAG, "Initializing Box64...");
            AppLogger.info(TAG, "Data dir: " + dataDir);
            AppLogger.info(TAG, "Native lib dir: " + nativeLibDir);
            
            boolean result = GameLauncher.initBox64(dataDir, nativeLibDir);
            
            if (result) {
                AppLogger.info(TAG, "Box64 initialized successfully");
            } else {
                AppLogger.error(TAG, "Box64 initialization failed");
            }
            
            return result;
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to initialize Box64", e);
            return false;
        }
    }
    
    /**
     * 检查 SteamCMD 是否已安装
     * 
     * @param context Android 上下文
     * @return true 已安装，false 未安装
     */
    public static boolean checkSteamCMDInstalled(Context context) {
        String steamcmdDir = com.app.ralaunch.utils.SteamCMDExtractor.getSteamCMDDir(context);
        File steamcmdBinary = new File(steamcmdDir, STEAMCMD_BINARY);
        
        boolean exists = steamcmdBinary.exists() && steamcmdBinary.canRead();
        AppLogger.info(TAG, "SteamCMD check: " + steamcmdBinary.getAbsolutePath() + " -> " + (exists ? "FOUND" : "NOT FOUND"));
        
        return exists;
    }
    
    /**
     * 启动 SteamCMD（交互式模式）
     * 
     * @param context Android 上下文
     * @return 0 成功，负数失败
     */
    public static int launchSteamCMD(Context context) {
        return launchSteamCMD(context, null);
    }
    
    /**
     * 启动 SteamCMD（带参数）
     * 
     * @param context Android 上下文
     * @param args SteamCMD 命令行参数（可为 null）
     * @return 0 成功，负数失败
     */
    public static int launchSteamCMD(Context context, String[] args) {
        AppLogger.info(TAG, "========================================");
        AppLogger.info(TAG, "Launching SteamCMD via Box64");
        AppLogger.info(TAG, "========================================");
        
        try {
            // 确保 Box64 已初始化（会自动解压 SteamCMD）
            if (!ensureBox64Initialized(context)) {
                AppLogger.error(TAG, "Box64 initialization failed");
                return -1;
            }
            
            // 获取 SteamCMD 路径
            String steamcmdDir = com.app.ralaunch.utils.SteamCMDExtractor.getSteamCMDDir(context);
            File steamcmdBinary = new File(steamcmdDir, STEAMCMD_BINARY);
            
            if (!steamcmdBinary.exists()) {
                AppLogger.error(TAG, "SteamCMD binary not found: " + steamcmdBinary.getAbsolutePath());
                AppLogger.error(TAG, "Please ensure SteamCMD is extracted from assets");
                return -2;
            }
            
            if (!steamcmdBinary.canRead()) {
                AppLogger.warn(TAG, "SteamCMD binary is not readable, attempting to fix permissions...");
                steamcmdBinary.setReadable(true, false);
                steamcmdBinary.setExecutable(true, false);
            }
            
            // 确保 linux32 目录下的库文件也有执行权限
            File linux32Dir = new File(steamcmdDir, "linux32");
            if (linux32Dir.exists() && linux32Dir.isDirectory()) {
                File[] files = linux32Dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            file.setReadable(true, false);
                            file.setExecutable(true, false);
                        }
                    }
                }
            }
            
            AppLogger.info(TAG, "SteamCMD path: " + steamcmdBinary.getAbsolutePath());
            
            // 构建命令行参数
            // 注意：直接运行二进制文件，而不是 shell 脚本
            // 使用绝对路径，native 层会从路径中提取工作目录并设置为 steamcmd 目录
            java.util.ArrayList<String> cmdArgs = new java.util.ArrayList<>();
            cmdArgs.add(steamcmdBinary.getAbsolutePath());
            
            if (args != null && args.length > 0) {
                for (String arg : args) {
                    cmdArgs.add(arg);
                }
            }
            
            String[] finalArgs = cmdArgs.toArray(new String[0]);
            
            AppLogger.info(TAG, "Command: box64 " + String.join(" ", finalArgs));
            AppLogger.info(TAG, "Working directory (will be set to): " + steamcmdDir);
            AppLogger.info(TAG, "Full binary path: " + steamcmdBinary.getAbsolutePath());
            
            // 通过 Box64 运行
            // 注意：native 层会检测 SteamCMD 路径，并将工作目录设置为 steamcmd 目录
            // 然后使用相对路径 linux32/steamcmd 来运行
            int result = GameLauncher.runBox64(finalArgs);
            
            AppLogger.info(TAG, "SteamCMD exited with code: " + result);
            return result;
            
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to launch SteamCMD", e);
            return -3;
        }
    }
    
    /**
     * 启动 SteamCMD 并执行命令（非交互式）
     * 
     * @param context Android 上下文
     * @param commands SteamCMD 命令（例如："+login anonymous +quit"）
     * @return 0 成功，负数失败
     */
    public static int launchSteamCMDWithCommands(Context context, String commands) {
        if (commands == null || commands.trim().isEmpty()) {
            return launchSteamCMD(context);
        }
        
        // 将命令字符串拆分为参数
        String[] args = commands.trim().split("\\s+");
        return launchSteamCMD(context, args);
    }
}

