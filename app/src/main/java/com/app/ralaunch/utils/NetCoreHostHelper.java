package com.app.ralaunch.utils;

import android.content.Context;

import com.app.ralaunch.netcore.NetCoreManager;

import java.io.File;

/**
 * .NET Core Host 辅助类
 *
 * 提供便捷的方法来运行 .NET 程序集
 * 封装 NetCoreManager 提供更简单的 API
 */
public class NetCoreHostHelper {

    private static final String TAG = "NetCoreHostHelper";
    private static boolean initialized = false;

    /**
     * 清理运行时并重置初始化标志
     */
    public static synchronized void cleanup() {
        AppLogger.info(TAG, "清理 .NET 运行时...");
        NetCoreManager.nativeCleanup();
        initialized = false;
        AppLogger.info(TAG, "运行时清理完成");
    }

    /**
     * 初始化 .NET 运行时（只需调用一次）
     *
     * @param context Android 上下文
     * @param frameworkMajor 框架主版本号（默认 10 for .NET 10）
     */
    public static synchronized void initialize(Context context, int frameworkMajor) {
        if (initialized) {
            AppLogger.debug(TAG, "已初始化，跳过");
            return;
        }

        String dotnetRoot = RuntimeManager.getDotnetPath(context);
        AppLogger.info(TAG, "初始化 .NET 运行时: DOTNET_ROOT=" + dotnetRoot + ", framework=" + frameworkMajor);

        // 注意：实际的 nativeInit 调用在 runAssemblyInternal 中进行
        // 这里只是标记需要初始化，不设置 initialized = true
    }

    /**
     * 初始化 .NET 运行时（自动根据选择的运行时版本）
     *
     * <p>此方法会自动从 RuntimeManager 获取当前选择的运行时版本，
     * 并使用其主版本号进行初始化。如果未选择版本或获取失败，则默认使用 .NET 10。
     *
     * @param context Android 上下文
     */
    public static void initialize(Context context) {
        // 获取当前选择的运行时版本
        String selectedVersion = RuntimeManager.getSelectedVersion(context);
        int frameworkMajor = 10; // 默认 .NET 10

        if (selectedVersion != null) {
            int major = RuntimeManager.getMajorVersion(selectedVersion);
            if (major > 0) {
                frameworkMajor = major;
                AppLogger.info(TAG, "使用选择的运行时版本: " + selectedVersion + " (主版本: " + frameworkMajor + ")");
            } else {
                AppLogger.warn(TAG, "无法解析运行时版本 " + selectedVersion + "，使用默认版本 .NET 10");
            }
        } else {
            AppLogger.warn(TAG, "未选择运行时版本，使用默认版本 .NET 10");
        }

        initialize(context, frameworkMajor);
    }

    /**
     * 运行 .NET 程序集（使用 NetCoreManager 的包装方法）
     *
     * 此方法会在运行前清理并重新初始化运行时，适用于启动游戏等需要独占运行时的场景
     *
     * @param context Android 上下文
     * @param appDir 程序集所在目录
     * @param assemblyName 程序集名称（如 "MyApp.dll"）
     * @param args 命令行参数
     * @return 程序集的标准输出（目前返回空字符串，输出在 logcat 中）
     * @throws Exception 运行失败时抛出异常
     */
    public static String runAssembly(Context context, String appDir, String assemblyName, String[] args) throws Exception {
        return runAssemblyInternal(context, appDir, assemblyName, args, true);
    }

    /**
     * 运行 .NET 程序集（不清理运行时，适用于工具类程序）
     *
     * 此方法不会清理运行时，可以连续运行多个工具程序（如 AssemblyChecker）
     * 但不适合运行游戏，因为游戏需要独占运行时
     *
     * @param context Android 上下文
     * @param appDir 程序集所在目录
     * @param assemblyName 程序集名称（如 "MyApp.dll"）
     * @param args 命令行参数（会传递到 C# Main 方法）
     * @return 程序集的标准输出（目前返回空字符串，输出在 logcat 中）
     * @throws Exception 运行失败时抛出异常
     */
    public static String runAssemblyWithoutCleanup(Context context, String appDir, String assemblyName, String[] args) throws Exception {
        return runAssemblyInternal(context, appDir, assemblyName, args, false);
    }

    /**
     * 内部方法：运行 .NET 程序集
     */
    private static String runAssemblyInternal(Context context, String appDir, String assemblyName, String[] args, boolean cleanupBefore) throws Exception {
        String dotnetRoot = RuntimeManager.getDotnetPath(context);
        File assemblyFile = new File(appDir, assemblyName);

        if (!assemblyFile.exists()) {
            throw new Exception("程序集不存在: " + assemblyFile.getAbsolutePath());
        }

        // 获取运行时主版本号
        String selectedVersion = RuntimeManager.getSelectedVersion(context);
        int frameworkMajor = 8; // 默认 .NET 8
        if (selectedVersion != null) {
            int major = RuntimeManager.getMajorVersion(selectedVersion);
            if (major > 0) {
                frameworkMajor = major;
            }
        }

        AppLogger.info(TAG, "运行程序集: " + assemblyFile.getAbsolutePath());

        if (cleanupBefore) {
            // ⚠️ 清理运行时（用于启动游戏等独占场景）
            AppLogger.info(TAG, "清理运行时...");
            NetCoreManager.nativeCleanup();
            initialized = false;
        }

        // 使用 NetCoreManager 初始化运行时（如果需要）
        if (!initialized) {
            AppLogger.info(TAG, "初始化 .NET 运行时...");
            int result = NetCoreManager.nativeInit(dotnetRoot, frameworkMajor);
            if (result != 0) {
                String error = NetCoreManager.nativeGetLastError();
                throw new Exception("初始化运行时失败: " + error);
            }
            initialized = true;
            AppLogger.info(TAG, "运行时初始化成功");
        }

        // 使用 NetCoreManager 运行程序集
        // nativeRunApp 现在已经支持参数传递到 C# Main 方法
        int result = NetCoreManager.nativeRunApp(appDir, assemblyName, args.length, args);

        if (result != 0) {
            String error = NetCoreManager.nativeGetLastError();
            throw new Exception("运行程序集失败: " + error);
        }

        // TODO: 实现捕获程序输出的机制
        // 目前 run_app 直接将输出打印到 logcat
        // 可以通过 LogcatReader 捕获输出
        AppLogger.info(TAG, "程序集运行完成");
        return "";
    }

    /**
     * 运行 .NET 程序集（无参数版本）
     *
     * @param context Android 上下文
     * @param appDir 程序集所在目录
     * @param assemblyName 程序集名称
     * @return 程序集的标准输出
     * @throws Exception 运行失败时抛出异常
     */
    public static String runAssembly(Context context, String appDir, String assemblyName) throws Exception {
        return runAssembly(context, appDir, assemblyName, new String[0]);
    }

    /**
     * 运行 .NET 程序集并返回退出码（不抛出异常）
     *
     * 此方法适用于需要检查退出码的工具程序（如 AssemblyChecker、InstallerTools）
     * 使用 runtime config 方式运行，支持在已加载的 CoreCLR 中运行（secondary context）
     * 不会因为非 0 退出码而抛出异常
     *
     * @param context Android 上下文
     * @param appDir 程序集所在目录
     * @param assemblyName 程序集名称
     * @param args 命令行参数
     * @return 程序退出码（0 表示成功）
     * @throws Exception 仅在运行时初始化失败时抛出
     */
    public static int runAssemblyForExitCode(Context context, String appDir, String assemblyName, String[] args) throws Exception {
        String dotnetRoot = RuntimeManager.getDotnetPath(context);
        File assemblyFile = new File(appDir, assemblyName);

        if (!assemblyFile.exists()) {
            throw new Exception("程序集不存在: " + assemblyFile.getAbsolutePath());
        }

        // 获取运行时主版本号
        String selectedVersion = RuntimeManager.getSelectedVersion(context);
        int frameworkMajor = 8; // 默认 .NET 8
        if (selectedVersion != null) {
            int major = RuntimeManager.getMajorVersion(selectedVersion);
            if (major > 0) {
                frameworkMajor = major;
            }
        }

        AppLogger.info(TAG, "运行工具程序: " + assemblyFile.getAbsolutePath());

        // 确保 NetCoreManager 已初始化
        if (!NetCoreManager.initialize(context, frameworkMajor)) {
            throw new Exception("初始化 .NET 运行时失败");
        }

        // 使用 NetCoreManager.runTool 运行工具程序
        // 这使用 initialize_for_runtime_config，可以在已加载的 CoreCLR 中运行
        int result = NetCoreManager.runTool(appDir, assemblyName, args);

        AppLogger.info(TAG, "工具程序退出码: " + result);
        return result;
    }
}
