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
 *   <li>多种启动模式：应用程序主机模式、直接程序集启动</li>
 *   <li>多版本运行时支持（.NET 6/7/8/9/10）</li>
 *   <li>运行时版本自动选择和手动指定</li>
 *   <li>游戏本体路径支持（用于模组加载器如 tModLoader）</li>
 * </ul>
 * 
 * <p>启动流程：
 * <ol>
 *   <li>Java 层设置启动参数（程序集路径、运行时路径等）</li>
 *   <li>参数通过 JNI 传递给 Native 层</li>
 *   <li>Native 层初始化 CoreCLR 运行时</li>
 *   <li>CoreCLR 加载并执行 .NET 程序集</li>
 * </ol>
 * 
 * @author RA Launcher Team
 * @see com.app.ralaunch.utils.RuntimeManager
 */
public class GameLauncher {
    private static final String TAG = "GameLauncher";

    /**
     * 设置基础启动参数（Native 方法）
     * 
     * @param appPath .NET 程序集路径
     * @param dotnetPath .NET 运行时根目录路径
     */
    private static native void setLaunchParams(String appPath, String dotnetPath);
    
    /**
     * 设置带运行时版本的启动参数（Native 方法）
     * 
     * @param appPath .NET 程序集路径
     * @param dotnetPath .NET 运行时根目录路径
     * @param frameworkVersion 指定的框架版本（如 "8.0.1"）
     */
    private static native void setLaunchParamsWithRuntime(String appPath, String dotnetPath, String frameworkVersion);
    
    /**
     * 设置详细日志模式（Native 方法）
     * 
     * @param enabled 是否启用详细日志（true = 启用，false = 禁用）
     */
    private static native void setVerboseLogging(boolean enabled);
    
    /**
     * 设置完整启动参数（Native 方法，用于 CoreCLR 直接启动）
     * 
     * @param appPath .NET 程序集路径
     * @param dotnetPath .NET 运行时根目录路径
     * @param appDir 应用程序目录
     * @param trustedAssemblies 受信程序集列表（冒号分隔）
     * @param nativeSearchPaths 原生库搜索路径（冒号分隔）
     * @param mainAssemblyPath 主程序集路径
     */
    private static native void setLaunchParamsFull(String appPath, String dotnetPath, String appDir,
                                                  String trustedAssemblies, String nativeSearchPaths,
                                                  String mainAssemblyPath);
    
    /**
     * 设置带游戏本体路径的启动参数（Native 方法，用于 tModLoader 等模组加载器）
     * 
     * @param appPath .NET 程序集路径
     * @param gameBodyPath 游戏本体路径
     * @param dotnetPath .NET 运行时根目录路径
     */
    private static native void setLaunchParamsWithGameBody(String appPath, String gameBodyPath, String dotnetPath);

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
            Log.d(TAG, "Preparing to launch app in host mode: " + assemblyPath);
            
            // 设置详细日志模式
            boolean verboseLogging = RuntimePreference.isVerboseLogging(context);
            setVerboseLogging(verboseLogging);
            Log.d(TAG, "Verbose logging: " + (verboseLogging ? "enabled" : "disabled"));

            // 检查传入的是否是完整路径
            File potentialAssembly = new File(assemblyPath);
            if (potentialAssembly.exists() && potentialAssembly.isFile()) {
                // 如果传入的是文件路径，直接使用它
                Log.d(TAG, "Using direct assembly path: " + assemblyPath);
                return launchAssemblyDirect(context, assemblyPath);
            }

            // 定义路径
            File appDir = context.getFilesDir();
            File appsDir = context.getExternalFilesDir(assemblyPath);
            if (appsDir == null) {
                appsDir = new File(context.getExternalFilesDir(null), assemblyPath);
            }

            // dotnet 运行时根目录
            File runtimeDir = new File(appDir, "dotnet");
            String frameworkVersion = resolvePreferredFrameworkVersion(runtimeDir, context);

            // 应用程序文件
            File dllFile = new File(assemblyPath ,assemblyName + ".dll");

            // 打印路径以供调试
            Log.d(TAG, "App DLL: " + dllFile.getAbsolutePath());
            Log.d(TAG, "Dotnet Runtime: " + runtimeDir.getAbsolutePath());
            Log.d(TAG, "Preferred Framework: " + (frameworkVersion == null ? "<auto>" : frameworkVersion));

            // 校验关键文件
            if (!dllFile.exists()) {
                Log.e(TAG, "App DLL not found: " + dllFile.getAbsolutePath());
                return -1;
            }

            if (!runtimeDir.exists()) {
                Log.e(TAG, "Dotnet runtime not found: " + runtimeDir.getAbsolutePath());
                return -1;
            }

            // 设置启动参数
            Log.d(TAG, "Setting launch parameters...");
            if (frameworkVersion != null) {
                setLaunchParamsWithRuntime(dllFile.getAbsolutePath(), runtimeDir.getAbsolutePath(), frameworkVersion);
            } else {
                setLaunchParams(dllFile.getAbsolutePath(), runtimeDir.getAbsolutePath());
            }

            Log.d(TAG, "Launch parameters set successfully");
            return 0; // 返回0表示参数设置成功，实际执行在SDL_main中

        } catch (Exception e) {
            Log.e(TAG, "Error in launchDotnetAppHost: " + e.getMessage(), e);
            return -1;
        }
    }

    /**
     * 直接启动指定的程序集文件
     * 
     * <p>此方法使用 CoreCLR 直接启动模式，不依赖 hostfxr。
     * 它会构建完整的受信程序集列表（TPA）和原生库搜索路径（NSP），
     * 并将所有参数传递给 Native 层。
     * 
     * <p>此方法比 {@link #launchDotnetAppHost} 提供更细粒度的控制，
     * 适用于需要自定义加载逻辑的场景。
     * 
     * @param context Android 上下文
     * @param assemblyPath .NET 程序集的完整路径
     * @return 0 表示参数设置成功，-1 表示失败
     * 
     * @see com.app.ralaunch.utils.RuntimeManager#buildTrustedAssemblies
     * @see com.app.ralaunch.utils.RuntimeManager#buildNativeSearchPaths
     */
    public static int launchAssemblyDirect(Context context, String assemblyPath) {
        try {
            Log.d(TAG, "Preparing to launch assembly directly: " + assemblyPath);
            
            // 设置详细日志模式
            boolean verboseLogging = RuntimePreference.isVerboseLogging(context);
            setVerboseLogging(verboseLogging);
            Log.d(TAG, "Verbose logging: " + (verboseLogging ? "enabled" : "disabled"));

            File assemblyFile = new File(assemblyPath);
            if (!assemblyFile.exists()) {
                Log.e(TAG, "Assembly file not found: " + assemblyPath);
                return -1;
            }

            // 使用全参数透传（coreclr 直启，不依赖 hostfxr）
            java.io.File appDir = assemblyFile.getParentFile();

            java.io.File dotnetRoot = com.app.ralaunch.utils.RuntimeManager.getDotnetRoot(context);
            String selected = com.app.ralaunch.utils.RuntimeManager.getSelectedVersion(context);
            if (selected == null) { Log.e(TAG, "No runtime version installed"); return -1; }
            java.io.File runtimeVerDir = new java.io.File(com.app.ralaunch.utils.RuntimeManager.getSharedRoot(context), selected);
            if (!runtimeVerDir.exists()) { Log.e(TAG, "Runtime version dir missing: " + runtimeVerDir); return -1; }
            String tpa = com.app.ralaunch.utils.RuntimeManager.buildTrustedAssemblies(runtimeVerDir, appDir);
            String nsp = com.app.ralaunch.utils.RuntimeManager.buildNativeSearchPaths(runtimeVerDir, appDir);
            Log.d(TAG, "Using runtime version: " + selected + ", TPA size=" + tpa.length());
            setLaunchParamsFull(assemblyFile.getAbsolutePath(), dotnetRoot.getAbsolutePath(), appDir.getAbsolutePath(), tpa, nsp, assemblyFile.getAbsolutePath());

            Log.d(TAG, "Launch parameters set successfully");
            return 0;

        } catch (Exception e) {
            Log.e(TAG, "Error in launchAssemblyDirect: " + e.getMessage(), e);
            return -1;
        }
    }
    
    /**
     * 带游戏本体路径启动程序集
     * 
     * <p>此方法专为模组加载器设计（如 tModLoader），这些加载器需要同时指定：
     * <ul>
     *   <li>加载器程序集路径（assemblyPath）</li>
     *   <li>游戏本体路径（gameBodyPath）</li>
     * </ul>
     * 
     * <p>加载器会在运行时加载游戏本体并应用模组。
     * 
     * @param context Android 上下文
     * @param assemblyPath 模组加载器程序集路径
     * @param gameBodyPath 游戏本体路径
     * @param assemblyName 程序集名称（不含 .dll 扩展名）
     * @return 0 表示参数设置成功，-1 表示失败
     * 
     * @see #launchDotnetAppHost
     */
    public static int launchDotnetAppHostWithGameBody(Context context, String assemblyPath, String gameBodyPath, String assemblyName) {
        try {
            Log.d(TAG, "Preparing to launch app with game body: " + assemblyPath);
            
            // 设置详细日志模式
            boolean verboseLogging = RuntimePreference.isVerboseLogging(context);
            setVerboseLogging(verboseLogging);
            Log.d(TAG, "Verbose logging: " + (verboseLogging ? "enabled" : "disabled"));
            Log.d(TAG, "Game body path: " + gameBodyPath);

            File assemblyFile = new File(assemblyPath);
            if (!assemblyFile.exists()) {
                Log.e(TAG, "Assembly file not found: " + assemblyPath);
                return -1;
            }
            
            File gameBodyFile = new File(gameBodyPath);
            if (!gameBodyFile.exists()) {
                Log.e(TAG, "Game body file not found: " + gameBodyPath);
                return -1;
            }

            // dotnet 运行时根目录
            File runtimeDir = new File(context.getFilesDir(), "dotnet");
            String frameworkVersion = resolvePreferredFrameworkVersion(runtimeDir, context);

            // 打印路径以供调试
            Log.d(TAG, "Assembly: " + assemblyFile.getAbsolutePath());
            Log.d(TAG, "Game Body: " + gameBodyFile.getAbsolutePath());
            Log.d(TAG, "Dotnet Runtime: " + runtimeDir.getAbsolutePath());
            Log.d(TAG, "Preferred Framework: " + (frameworkVersion == null ? "<auto>" : frameworkVersion));

            if (!runtimeDir.exists()) {
                Log.e(TAG, "Dotnet runtime not found: " + runtimeDir.getAbsolutePath());
                return -1;
            }

            // 设置启动参数（包括游戏本体路径）
            Log.d(TAG, "Setting launch parameters with game body...");
            if (frameworkVersion != null) {
                // 先设置包含运行时版本的参数
                setLaunchParamsWithRuntime(assemblyFile.getAbsolutePath(), runtimeDir.getAbsolutePath(), frameworkVersion);
            }
            // 再设置包含 game body 的启动参数（沿用 dotnetPath）
            setLaunchParamsWithGameBody(assemblyFile.getAbsolutePath(), gameBodyFile.getAbsolutePath(), runtimeDir.getAbsolutePath());

            Log.d(TAG, "Launch parameters set successfully");
            return 0;

        } catch (Exception e) {
            Log.e(TAG, "Error in launchDotnetAppHostWithGameBody: " + e.getMessage(), e);
            return -1;
        }
    }

    /**
     * 解析首选框架版本
     * 
     * <p>版本解析优先级：
     * <ol>
     *   <li>RuntimeManager 选择的版本（最高优先级）</li>
     *   <li>dotnet_framework 偏好设置（net7/net8/net9/net10）</li>
     *   <li>自动选择（返回 null，由 Native 层选择最高版本）</li>
     * </ol>
     * 
     * <p>如果首选版本不存在，会回退到下一优先级。
     * 
     * @param runtimeDir 运行时根目录
     * @param context Android 上下文
     * @return 框架版本号（如 "8.0.1"），null 表示自动选择
     * 
     * @see com.app.ralaunch.utils.RuntimeManager#getSelectedVersion
     * @see com.app.ralaunch.utils.RuntimeManager#getLatestVersionForMajor
     */
    private static String resolvePreferredFrameworkVersion(File runtimeDir, Context context) {
        try {
            // 1. 优先使用 RuntimeManager 选择的版本
            String selectedVersion = com.app.ralaunch.utils.RuntimeManager.getSelectedVersion(context);
            if (selectedVersion != null && !selectedVersion.isEmpty()) {
                File versionDir = new File(
                    new File(new File(runtimeDir, "shared"), "Microsoft.NETCore.App"), 
                    selectedVersion
                );
                if (versionDir.exists()) {
                    Log.d(TAG, "Using RuntimeManager selected version: " + selectedVersion);
                    return selectedVersion;
                } else {
                    Log.w(TAG, "Selected version directory not found: " + versionDir);
                }
            }

            // 2. 回退：读取 dotnet_framework 偏好设置
            android.content.SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            String pref = prefs.getString("dotnet_framework", "auto");
            if (pref == null) pref = "auto";

            // 如果是 auto，返回 null 表示让 native 自动挑选最高版本
            if (pref.equalsIgnoreCase("auto")) {
                Log.d(TAG, "Using auto framework version selection");
                return null;
            }

            // 解析主版本号偏好
            int preferredMajor = -1;
            if (pref.equalsIgnoreCase("net6")) preferredMajor = 6;
            else if (pref.equalsIgnoreCase("net7")) preferredMajor = 7;
            else if (pref.equalsIgnoreCase("net8")) preferredMajor = 8;
            else if (pref.equalsIgnoreCase("net9")) preferredMajor = 9;
            else if (pref.equalsIgnoreCase("net10")) preferredMajor = 10;

            if (preferredMajor != -1) {
                String versionForMajor = com.app.ralaunch.utils.RuntimeManager.getLatestVersionForMajor(
                    context, preferredMajor);
                if (versionForMajor != null) {
                    Log.d(TAG, "Using latest version for major " + preferredMajor + ": " + versionForMajor);
                    return versionForMajor;
                }
            }

            Log.d(TAG, "No specific framework version resolved, using auto");
            return null;

        } catch (Exception e) {
            Log.w(TAG, "resolvePreferredFrameworkVersion failed: " + e.getMessage(), e);
            return null;
        }
    }
}