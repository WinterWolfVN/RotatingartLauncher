package com.app.ralaunch.ui.game

import android.content.Context
import android.os.Build
import com.app.ralaunch.R
import com.app.ralaunch.core.GameLauncher
import com.app.ralaunch.patch.PatchManager
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.utils.AppLogger
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 游戏界面 Presenter
 * 处理游戏启动和崩溃报告相关的业务逻辑
 */
class GamePresenter : GameContract.Presenter {

    companion object {
        private const val TAG = "GamePresenter"
        private const val MAX_LOG_LINES = 200
        private const val MAX_LOG_LENGTH = 50000
    }

    private var viewRef: WeakReference<GameContract.View>? = null
    private val view: GameContract.View? get() = viewRef?.get()

    override fun attach(view: GameContract.View) {
        this.viewRef = WeakReference(view)
    }

    override fun detach() {
        this.viewRef = null
    }

    override fun launchGame(): Int {
        val view = this.view ?: return -1
        
        // 重置 GameLauncher 初始化状态，确保每次启动都重新初始化
        GameLauncher.resetInitializationState()
        
        return try {
            val intent = view.getActivityIntent()

            val assemblyPath = intent.getStringExtra("ASSEMBLY_PATH")
            val runtime = intent.getStringExtra("RUNTIME") // "dotnet" or "box64"

            if (assemblyPath.isNullOrEmpty()) {
                AppLogger.error(TAG, "Assembly path is null or empty")
                view.runOnMainThread {
                    view.showError(
                        view.getStringRes(R.string.game_launch_failed),
                        view.getStringRes(R.string.game_launch_assembly_path_empty)
                    )
                }
                return -1
            }

            val assemblyFile = File(assemblyPath)
            if (!assemblyFile.exists() || !assemblyFile.isFile) {
                AppLogger.error(TAG, "Assembly file not found: $assemblyPath")
                view.runOnMainThread {
                    view.showError(
                        view.getStringRes(R.string.game_launch_failed),
                        view.getStringRes(R.string.game_launch_assembly_not_exist, assemblyPath)
                    )
                }
                return -2
            }
            
            // 应用游戏特定的默认渲染器
            val defaultRenderer = intent.getStringExtra("DEFAULT_RENDERER")
            if (!defaultRenderer.isNullOrEmpty()) {
                AppLogger.info(TAG, "Applying game-specific renderer: $defaultRenderer")
                val appContext: Context = KoinJavaComponent.get(Context::class.java)
                com.app.ralaunch.renderer.RendererConfig.setRenderer(appContext, defaultRenderer)
            }

            val exitCode = when (runtime) {
                "box64" -> {
                    AppLogger.info(TAG, "Launching with Box64: $assemblyPath")
                    val appContext: Context = KoinJavaComponent.get(Context::class.java)
                    GameLauncher.launchBox64Game(
                        appContext,
                        assemblyPath
                    ).also { code ->
                        onGameExit(code, if (code != 0) "Box64 启动失败" else null)
                    }
                }
                else -> {
                    val enabledPatchIds = intent.getStringArrayListExtra("ENABLED_PATCH_IDS")
                    val patchManager: PatchManager? = try {
                        KoinJavaComponent.getOrNull(PatchManager::class.java)
                    } catch (e: Exception) { null }
                    val enabledPatches = enabledPatchIds?.takeIf { it.isNotEmpty() }?.let {
                        patchManager?.getPatchesByIds(it)
                    }
                    
                    GameLauncher.launchDotNetAssembly(assemblyPath, emptyArray(), enabledPatches).also { code ->
                        onGameExit(code, GameLauncher.getLastErrorMessage())
                    }
                }
            }

            if (exitCode == 0) {
                AppLogger.info(TAG, "Game exited successfully.")
            } else {
                AppLogger.error(TAG, "Failed to launch game: $exitCode")
            }
            exitCode
        } catch (e: Exception) {
            AppLogger.error(TAG, "Exception in launchGame: ${e.message}", e)
            view.runOnMainThread {
                view.showError(view.getStringRes(R.string.game_launch_failed), e.message ?: "Unknown error")
            }
            -3
        }
    }

    override fun onGameExit(exitCode: Int, errorMessage: String?) {
        val view = this.view ?: return

        view.runOnMainThread {
            if (exitCode == 0) {
                view.showToast(view.getStringRes(R.string.game_completed_successfully))
            } else {
                showCrashReport(exitCode, errorMessage)
            }
        }
    }

    private fun showCrashReport(exitCode: Int, errorMessage: String?) {
        val view = this.view ?: return

        try {
            val nativeError = try {
                GameLauncher.getLastErrorMessage()
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Failed to get native error", e)
                null
            }

            val logcatLogs = getRecentLogcatLogs()
            val message = buildExitMessage(view, exitCode, errorMessage)
            val errorDetails = buildErrorDetails(view, exitCode, nativeError, errorMessage)
            val stackTrace = buildStackTrace(exitCode, nativeError, logcatLogs, errorMessage)

            view.showCrashReport(
                stackTrace = stackTrace,
                errorDetails = errorDetails,
                exceptionClass = "GameExitException",
                exceptionMessage = message
            )
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to show crash report", e)
            val message = buildExitMessage(view, exitCode, errorMessage)
            view.showError(view.getStringRes(R.string.game_run_failed), message)
            view.finishActivity()
        }
    }

    private fun buildExitMessage(view: GameContract.View, exitCode: Int, errorMessage: String?): String {
        return if (!errorMessage.isNullOrEmpty()) {
            "$errorMessage\n${view.getStringRes(R.string.game_exit_code, exitCode)}"
        } else {
            view.getStringRes(R.string.game_exit_code, exitCode)
        }
    }

    private fun buildErrorDetails(
        view: GameContract.View,
        exitCode: Int,
        nativeError: String?,
        errorMessage: String?
    ): String {
        return buildString {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            append("发生时间: ${sdf.format(Date())}\n\n")

            val versionName = view.getAppVersionName() ?: "未知"
            append("应用版本: $versionName\n")
            append("设备型号: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android 版本: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n\n")

            append("错误类型: 游戏异常退出\n")
            append("退出代码: $exitCode\n")

            if (!nativeError.isNullOrEmpty()) {
                append("C层错误: $nativeError\n")
            }

            if (!errorMessage.isNullOrEmpty()) {
                append("错误信息: $errorMessage\n")
            }
        }
    }

    private fun buildStackTrace(
        exitCode: Int,
        nativeError: String?,
        logcatLogs: String?,
        errorMessage: String?
    ): String {
        return buildString {
            append("游戏进程异常退出\n")
            append("退出代码: $exitCode\n\n")

            if (!nativeError.isNullOrEmpty()) {
                append("=== C层错误信息 ===\n")
                append("$nativeError\n\n")
            }

            if (!logcatLogs.isNullOrEmpty()) {
                append("=== Logcat 日志（最近错误） ===\n")
                append("$logcatLogs\n\n")
            }

            if (!errorMessage.isNullOrEmpty()) {
                append("=== 错误详情 ===\n")
                append(errorMessage)
            }
        }
    }

    private fun getRecentLogcatLogs(): String? {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-v", "time", "*:E", "*:W", 
                    "NetCoreHost:E", "GameLauncher:E", "SDL:E", "FNA3D:E")
            )

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val logs = StringBuilder()
            var lineCount = 0

            reader.useLines { lines ->
                lines.takeWhile { lineCount < MAX_LOG_LINES }
                    .filter { line ->
                        line.contains("ERROR") || line.contains("FATAL") ||
                        line.contains("Exception") || line.contains("Error") ||
                        line.contains("NetCoreHost") || line.contains("GameLauncher") ||
                        line.contains("SDL") || line.contains("FNA3D")
                    }
                    .forEach { line ->
                        logs.append(line).append("\n")
                        lineCount++
                    }
            }

            process.destroy()

            var result = logs.toString()
            if (result.length > MAX_LOG_LENGTH) {
                result = "...[日志已截断，仅显示最后部分]...\n" + 
                    result.substring(result.length - MAX_LOG_LENGTH)
            }

            result.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Failed to get logcat logs", e)
            null
        }
    }
}
