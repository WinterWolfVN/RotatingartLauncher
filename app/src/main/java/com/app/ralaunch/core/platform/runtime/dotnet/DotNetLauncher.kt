package com.app.ralaunch.core.platform.runtime.dotnet

import com.app.ralaunch.core.platform.runtime.EnvVarsManager
import com.app.ralaunch.core.common.util.AppLogger
import com.app.ralaunch.core.common.util.RuntimePreference

object DotNetLauncher {
    const val TAG = "DotNetLauncher"

    val hostfxrLastErrorMsg: String
        get() = getNativeDotNetLauncherHostfxrLastErrorMsg()

    /**
     * 启动 .NET 程序集
     * 在这里负责设置底层 runtime 环境变量并调用底层启动器
     * 如果需要进行游戏相关环境准备，请在 GameLauncher.launchDotNetAssembly 中进行
     * 不要在这里进行游戏相关环境准备，以免影响其他程序集的运行
     * @param assemblyPath 程序集路径
     * @param args 传递给程序集的参数
     * @return 程序集退出代码
     */
    fun hostfxrLaunch(assemblyPath: String, args: Array<String>): Int {
        val dotnetRoot = RuntimePreference.getDotnetRootPath() ?: run {
            AppLogger.error(TAG, "Failed to get dotnet root path")
            return -1
        }

        // Implementation to launch .NET assembly
        AppLogger.info(TAG, "Launching .NET assembly at $assemblyPath with arguments: ${args.joinToString(", ")}")
        AppLogger.info(TAG, "Using .NET root path: $dotnetRoot")

        EnvVarsManager.quickSetEnvVar("DOTNET_ROOT", dotnetRoot)
        CoreCLRConfig.applyConfigAndInitHooking()
        DotNetNativeLibraryLoader.loadAllLibraries(dotnetRoot)

        val exitCode = nativeDotNetLauncherHostfxrLaunch(assemblyPath, args, dotnetRoot)
        if (exitCode != 0) {
            val errorMsg = getNativeDotNetLauncherHostfxrLastErrorMsg()
            AppLogger.error(TAG, "Failed to launch .NET assembly. Exit code: $exitCode, Error: $errorMsg")
        } else {
            AppLogger.info(TAG, "Successfully launched .NET assembly.")
        }

        return exitCode
    }

    private external fun getNativeDotNetLauncherHostfxrLastErrorMsg(): String
    private external fun nativeDotNetLauncherHostfxrLaunch(assemblyPath: String, args: Array<String>, dotnetRoot: String): Int
}