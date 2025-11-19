package com.app.ralaunch.netcore;

import android.content.Context;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.utils.RuntimeManager;

/**
 * .NET Core 管理器 - JNI 包装类
 *
 * 提供便捷的 .NET Core 程序集调用接口
 */
public class NetCoreManager {

    private static final String TAG = "NetCoreManager";
    private static boolean initialized = false;
    private static String dotnetRoot = null;

    // 加载 native 库
    static {
        try {
            System.loadLibrary("main");
            AppLogger.info(TAG, "main 库加载成功");
        } catch (UnsatisfiedLinkError e) {
            AppLogger.error(TAG, "无法加载 main 库", e);
        }
    }

    /**
     * 初始化 .NET 运行时（只需调用一次）
     *
     * @param context Android 上下文
     * @param frameworkMajor 框架主版本号（10 = .NET 10）
     * @return true 成功，false 失败
     */
    public static synchronized boolean initialize(Context context, int frameworkMajor) {
        if (initialized) {
            AppLogger.debug(TAG, "已初始化，跳过");
            return true;
        }

        dotnetRoot = RuntimeManager.getDotnetPath(context);

        AppLogger.info(TAG, "初始化 .NET Core Manager");
        AppLogger.info(TAG, "  DOTNET_ROOT: " + dotnetRoot);
        AppLogger.info(TAG, "  Framework: .NET " + frameworkMajor);

        int result = nativeInit(dotnetRoot, frameworkMajor);

        if (result == 0) {
            initialized = true;
            AppLogger.info(TAG, "✓ 初始化成功");
            return true;
        } else {
            String error = nativeGetLastError();
            AppLogger.error(TAG, "✗ 初始化失败: " + error);
            return false;
        }
    }

    /**
     * 初始化 .NET 运行时（自动根据选择的运行时版本）
     *
     * <p>此方法会自动从 RuntimeManager 获取当前选择的运行时版本，
     * 并使用其主版本号进行初始化。如果未选择版本或获取失败，则默认使用 .NET 10。
     *
     * @param context Android 上下文
     * @return true 成功，false 失败
     */
    public static boolean initialize(Context context) {
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

        return initialize(context, frameworkMajor);
    }

    /**
     * 运行程序集（调用 Main 入口点）
     *
     * 此方法会将参数传递到 C# Main(string[] args) 方法
     *
     * @param appDir 程序集所在目录
     * @param assemblyName 程序集名称（如 "MyGame.dll"）
     * @param args 命令行参数（会传递到 C# Main 方法）
     * @return 退出码（0 表示成功）
     */
    public static int runApp(String appDir, String assemblyName, String[] args) {
        if (!initialized) {
            AppLogger.error(TAG, "未初始化，请先调用 initialize()");
            return -1;
        }

        AppLogger.info(TAG, "运行程序集: " + assemblyName);
        AppLogger.info(TAG, "  目录: " + appDir);
        if (args != null && args.length > 0) {
            AppLogger.info(TAG, "  参数数量: " + args.length);
        }

        int result = nativeRunApp(appDir, assemblyName, args != null ? args.length : 0, args);

        if (result != 0) {
            String error = nativeGetLastError();
            if (error != null) {
                AppLogger.error(TAG, "运行失败: " + error);
            }
        }

        return result;
    }

    /**
     * 运行程序集（无参数）
     */
    public static int runApp(String appDir, String assemblyName) {
        return runApp(appDir, assemblyName, null);
    }

    /**
     * 加载程序集并获取上下文句柄
     *
     * @param appDir 程序集所在目录
     * @param assemblyName 程序集名称
     * @return 上下文句柄（long 类型），失败返回 0
     */
    public static long loadAssembly(String appDir, String assemblyName) {
        if (!initialized) {
            AppLogger.error(TAG, "未初始化，请先调用 initialize()");
            return 0;
        }

        AppLogger.debug(TAG, "加载程序集: " + assemblyName);

        long handle = nativeLoadAssembly(appDir, assemblyName);

        if (handle == 0) {
            String error = nativeGetLastError();
            AppLogger.error(TAG, "加载失败: " + error);
        } else {
            AppLogger.debug(TAG, "程序集已加载，句柄: 0x" + Long.toHexString(handle));
        }

        return handle;
    }

    /**
     * 调用程序集的静态方法
     *
     * @param contextHandle 上下文句柄
     * @param typeName 类型全名（格式："Namespace.Class, Assembly"）
     * @param methodName 方法名称
     * @param delegateType 委托类型（可为 null）
     * @return 方法返回的函数指针，失败返回 0
     */
    public static long callMethod(long contextHandle, String typeName, String methodName, String delegateType) {
        if (contextHandle == 0) {
            AppLogger.error(TAG, "无效的上下文句柄");
            return 0;
        }

        AppLogger.debug(TAG, "调用方法: " + typeName + "::" + methodName);

        long result = nativeCallMethod(contextHandle, typeName, methodName, delegateType);

        if (result == 0 && delegateType != null) {
            String error = nativeGetLastError();
            if (error != null) {
                AppLogger.error(TAG, "调用失败: " + error);
            }
        }

        return result;
    }

    /**
     * 调用无返回值的方法
     */
    public static boolean callMethod(long contextHandle, String typeName, String methodName) {
        return callMethod(contextHandle, typeName, methodName, null) == 0;
    }

    /**
     * 获取程序集的属性值
     *
     * @param contextHandle 上下文句柄
     * @param typeName 类型全名
     * @param propertyName 属性名称
     * @param delegateType 委托类型
     * @return 属性值（函数指针），失败返回 0
     */
    public static long getProperty(long contextHandle, String typeName, String propertyName, String delegateType) {
        if (contextHandle == 0) {
            AppLogger.error(TAG, "无效的上下文句柄");
            return 0;
        }

        AppLogger.debug(TAG, "获取属性: " + typeName + "." + propertyName);

        long result = nativeGetProperty(contextHandle, typeName, propertyName, delegateType);

        if (result == 0) {
            String error = nativeGetLastError();
            if (error != null) {
                AppLogger.error(TAG, "获取失败: " + error);
            }
        }

        return result;
    }

    /**
     * 关闭程序集上下文
     */
    public static void closeContext(long contextHandle) {
        if (contextHandle != 0) {
            AppLogger.debug(TAG, "关闭上下文: 0x" + Long.toHexString(contextHandle));
            nativeCloseContext(contextHandle);
        }
    }

    /**
     * 获取最后一次错误
     */
    public static String getLastError() {
        return nativeGetLastError();
    }

    /**
     * 运行工具程序（使用 runtime config，支持在已加载的 CoreCLR 中运行）
     *
     * 此方法专门用于运行工具程序（如 AssemblyChecker、InstallerTools），
     * 与 runApp() 的区别：
     * - runApp() 使用 initialize_for_dotnet_command_line，会加载 CoreCLR（primary context）
     * - runTool() 使用 initialize_for_runtime_config，可以在已加载的 CoreCLR 中运行（secondary context）
     *
     * 重要：如果 CoreCLR 已被 runApp() 加载，则后续只能使用此方法，不能再用 runApp()
     *
     * @param appDir 工具程序所在目录
     * @param toolAssembly 工具程序集名称（如 "AssemblyChecker.dll"）
     * @param args 命令行参数
     * @return 工具程序退出码（Main方法的返回值）
     */
    public static int runTool(String appDir, String toolAssembly, String[] args) {
        if (!initialized) {
            AppLogger.error(TAG, "未初始化，请先调用 initialize()");
            return -1;
        }

        AppLogger.info(TAG, "运行工具程序: " + toolAssembly);
        AppLogger.info(TAG, "  目录: " + appDir);
        if (args != null && args.length > 0) {
            AppLogger.info(TAG, "  参数数量: " + args.length);
        }

        int result = nativeRunTool(appDir, toolAssembly, args != null ? args.length : 0, args);

        if (result != 0) {
            String error = nativeGetLastError();
            if (error != null) {
                AppLogger.error(TAG, "运行失败: " + error);
            }
        }

        return result;
    }

    /**
     * 运行工具程序（无参数）
     */
    public static int runTool(String appDir, String toolAssembly) {
        return runTool(appDir, toolAssembly, null);
    }

    /**
     * 清理所有资源
     */
    public static synchronized void cleanup() {
        if (initialized) {
            AppLogger.info(TAG, "清理资源");
            nativeCleanup();
            initialized = false;
        }
    }

    // ========== Native 方法 ==========

    public static native int nativeInit(String dotnetRoot, int frameworkMajor);
    public static native int nativeRunApp(String appDir, String assemblyName, int argc, String[] argv);
    public static native int nativeRunTool(String appDir, String toolAssembly, int argc, String[] argv);
    public static native long nativeLoadAssembly(String appDir, String assemblyName);
    public static native long nativeCallMethod(long contextHandle, String typeName, String methodName, String delegateType);
    public static native long nativeGetProperty(long contextHandle, String typeName, String propertyName, String delegateType);
    public static native void nativeCloseContext(long contextHandle);
    public static native String nativeGetLastError();
    public static native void nativeCleanup();
}
