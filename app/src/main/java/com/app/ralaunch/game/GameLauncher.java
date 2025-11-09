package com.app.ralaunch.game;

import android.content.Context;
import android.util.Log;

import com.app.ralaunch.utils.RuntimePreference;

import java.io.File;

/**
 * .NET 游戏启动器
 * 
 * <p>此类负责启动 .NET 应用程序（游戏），支持以下特性：
 * <ul>
 *   <li>使用 netcorehost API 直接启动程序集</li>
 *   <li>多版本运行时支持（.NET 6/7/8/9/10）</li>
 *   <li>运行时版本自动选择和手动指定</li>
 *   <li>游戏本体路径支持（用于模组加载器如 tModLoader）</li>
 * </ul>
 * 
 * <p>启动流程：
 * <ol>
 *   <li>Java 层设置启动参数（程序集路径、运行时路径等）</li>
 *   <li>参数通过 JNI 传递给 Native 层</li>
 *   <li>Native 层使用 netcorehost 加载 hostfxr</li>
 *   <li>hostfxr 初始化 .NET 运行时并执行程序集</li>
 * </ol>
 * 
 * @author RA Launcher Team
 * @see com.app.ralaunch.utils.RuntimeManager
 */
public class GameLauncher {
    private static final String TAG = "GameLauncher";

    /**
     * 【新】netcorehost API：设置启动参数
     * 
     * @param appDir 应用程序目录
     * @param mainAssembly 主程序集名称（如 "MyGame.dll"）
     * @param dotnetRoot .NET 运行时根目录（可为 null）
     * @param frameworkMajor 首选框架主版本号（0 = 自动选择最高版本）
     * @return 0 成功，负数失败
     */
    public static native int netcorehostSetParams(
        String appDir, 
        String mainAssembly, 
        String dotnetRoot, 
        int frameworkMajor);
    
    /**
     * 【新】netcorehost API：启动应用
     * 
     * @return 应用退出码
     */
    public static native int netcorehostLaunch();
    
    /**
     * 【新】netcorehost API：清理资源
     */
    public static native void netcorehostCleanup();

    /**
     * 使用应用程序主机模式启动 .NET 应用
     * 
     * <p>此方法通过 SDL_main 入口点启动应用，适用于大多数 .NET 游戏。
     * 它会自动解析首选的运行时版本，验证文件存在性，并设置启动参数。
     * 
     * @param context Android 上下文
     * @param assemblyPath 程序集路径或目录路径
     * @param assemblyName 程序集名称（不含 .dll 扩展名）
     * @return 0 表示参数设置成功，-1 表示失败
     * 
     * @see #launchAssemblyDirect(Context, String)
     */
    public static int launchDotnetAppHost(Context context, String assemblyPath,String assemblyName) {
        try {
            Log.i(TAG, "=== 使用 netcorehost API 启动 ===");
            
            // 检查传入的是否是完整路径
            File potentialAssembly = new File(assemblyPath);
            if (potentialAssembly.exists() && potentialAssembly.isFile()) {
                // 如果传入的是文件路径，直接使用它
                Log.i(TAG, "Detected file path, launching assembly directly");
                return launchAssemblyDirect(context, assemblyPath);
            }

            // 定义路径
            File appDir = new File(assemblyPath);
            if (!appDir.exists()) {
                appDir = context.getExternalFilesDir(assemblyPath);
                if (appDir == null) {
                    appDir = new File(context.getExternalFilesDir(null), assemblyPath);
                }
            }

            // 应用程序文件
            File dllFile = new File(appDir, assemblyName + ".dll");

            // 校验关键文件
            if (!dllFile.exists()) {
                Log.e(TAG, "App DLL not found: " + dllFile.getAbsolutePath());
                return -1;
            }

            // 获取 .NET 运行时根目录
            File runtimeDir = com.app.ralaunch.utils.RuntimeManager.getDotnetRoot(context);
            if (!runtimeDir.exists()) {
                Log.e(TAG, "Dotnet runtime not found: " + runtimeDir.getAbsolutePath());
                return -1;
            }

            // 解析首选框架主版本号
            int frameworkMajor = resolvePreferredFrameworkMajor(context);
            
            Log.i(TAG, "App Dir: " + appDir.getAbsolutePath());
            Log.i(TAG, "Assembly: " + dllFile.getName());
            Log.i(TAG, ".NET Root: " + runtimeDir.getAbsolutePath());
            Log.i(TAG, "Framework Major: " + frameworkMajor + " (0=auto)");

            // 使用 netcorehost API 设置启动参数
            int result = netcorehostSetParams(
                appDir.getAbsolutePath(),
                dllFile.getName(),
                runtimeDir.getAbsolutePath(),
                frameworkMajor
            );

            if (result != 0) {
                Log.e(TAG, "netcorehostSetParams failed: " + result);
                return -1;
            }

            Log.i(TAG, "✓ Launch parameters set successfully");
            return 0; // 返回0表示参数设置成功，实际执行在SDL_main中

        } catch (Exception e) {
            Log.e(TAG, "Error in launchDotnetAppHost: " + e.getMessage(), e);
            return -1;
        }
    }

    /**
     * 直接启动指定的程序集文件
     * 
     * <p>此方法使用 netcorehost API 直接启动程序集。
     * 通过 hostfxr 的 initialize_for_dotnet_command_line 接口启动。
     * 
     * <p>适用于直接运行独立的 .NET 程序集。
     * 
     * @param context Android 上下文
     * @param assemblyPath .NET 程序集的完整路径
     * @return 0 表示参数设置成功，-1 表示失败
     * 
     * @see #launchDotnetAppHost
     */
    public static int launchAssemblyDirect(Context context, String assemblyPath) {
        try {
            Log.i(TAG, "=== launchAssemblyDirect (netcorehost API) ===");

            File assemblyFile = new File(assemblyPath);
            if (!assemblyFile.exists()) {
                Log.e(TAG, "Assembly file not found: " + assemblyPath);
                return -1;
            }

            File appDir = assemblyFile.getParentFile();
            File dotnetRoot = com.app.ralaunch.utils.RuntimeManager.getDotnetRoot(context);
            
            if (!dotnetRoot.exists()) {
                Log.e(TAG, ".NET runtime not found: " + dotnetRoot);
                return -1;
            }

            // 解析首选框架主版本号
            int frameworkMajor = resolvePreferredFrameworkMajor(context);
            
            Log.i(TAG, "App Dir: " + appDir.getAbsolutePath());
            Log.i(TAG, "Assembly: " + assemblyFile.getName());
            Log.i(TAG, ".NET Root: " + dotnetRoot.getAbsolutePath());
            Log.i(TAG, "Framework Major: " + frameworkMajor);

            // 使用 netcorehost API
            int result = netcorehostSetParams(
                appDir.getAbsolutePath(),
                assemblyFile.getName(),
                dotnetRoot.getAbsolutePath(),
                frameworkMajor
            );

            if (result != 0) {
                Log.e(TAG, "netcorehostSetParams failed: " + result);
                return -1;
            }

            Log.i(TAG, "✓ Assembly launch parameters set");
            return 0;

        } catch (Exception e) {
            Log.e(TAG, "Error in launchAssemblyDirect: " + e.getMessage(), e);
            return -1;
        }
    }
    

    /**
     * 解析首选框架主版本号（用于 netcorehost API）
     * 
     * <p>版本解析优先级：
     * <ol>
     *   <li>RuntimeManager 选择的版本（提取主版本号）</li>
     *   <li>dotnet_framework 偏好设置（net7/net8/net9/net10）</li>
     *   <li>自动选择（返回 0，由 Native 层选择最高版本）</li>
     * </ol>
     * 
     * @param context Android 上下文
     * @return 框架主版本号（如 8 表示 .NET 8），0 表示自动选择
     */
    private static int resolvePreferredFrameworkMajor(Context context) {
        try {
            // 1. 优先使用 RuntimeManager 选择的版本
            String selectedVersion = com.app.ralaunch.utils.RuntimeManager.getSelectedVersion(context);
            if (selectedVersion != null && !selectedVersion.isEmpty()) {
                int major = com.app.ralaunch.utils.RuntimeManager.getMajorVersion(selectedVersion);
                if (major > 0) {
                    Log.i(TAG, "Using RuntimeManager selected version: " + selectedVersion + " (major=" + major + ")");
                    return major;
                }
            }

            // 2. 回退：读取 dotnet_framework 偏好设置
            android.content.SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            String pref = prefs.getString("dotnet_framework", "auto");
            if (pref == null) pref = "auto";

            // 如果是 auto，返回 0 表示让 native 自动挑选最高版本
            if (pref.equalsIgnoreCase("auto")) {
                Log.i(TAG, "Using auto framework selection");
                return 0;
            }

            // 解析主版本号偏好
            int preferredMajor = 0;
            if (pref.equalsIgnoreCase("net6")) preferredMajor = 6;
            else if (pref.equalsIgnoreCase("net7")) preferredMajor = 7;
            else if (pref.equalsIgnoreCase("net8")) preferredMajor = 8;
            else if (pref.equalsIgnoreCase("net9")) preferredMajor = 9;
            else if (pref.equalsIgnoreCase("net10")) preferredMajor = 10;

            if (preferredMajor > 0) {
                Log.i(TAG, "Using framework preference: net" + preferredMajor);
            }

            return preferredMajor;

        } catch (Exception e) {
            Log.w(TAG, "resolvePreferredFrameworkMajor failed: " + e.getMessage(), e);
            return 0; // 失败时默认自动选择
        }
    }


    /**
     * 递归删除目录及其所有内容
     */
    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }
    


    

}