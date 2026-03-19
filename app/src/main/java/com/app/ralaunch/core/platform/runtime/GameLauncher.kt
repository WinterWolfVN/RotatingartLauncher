package com.app.ralaunch.core.platform.runtime

import android.content.Context
import android.os.Environment
import android.system.Os
import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.core.common.util.AppLogger
import com.app.ralaunch.core.common.util.NativeMethods
import com.app.ralaunch.core.platform.android.ProcessLauncherService
import com.app.ralaunch.core.platform.runtime.dotnet.DotNetLauncher
import com.app.ralaunch.core.platform.runtime.renderer.RendererEnvironmentConfigurator
import com.app.ralaunch.feature.patch.data.Patch
import com.app.ralaunch.feature.patch.data.PatchManager
import org.koin.java.KoinJavaComponent
import org.libsdl.app.SDL
import java.io.File

object GameLauncher {

    private const val TAG = "GameLauncher"
    private const val DEFAULT_DATA_DIR_NAME = "RALauncher"
    private var isSDLJNIInitialized = false

    fun resetInitializationState() {
        isSDLJNIInitialized = false
        AppLogger.info(TAG, "初始化状态已重置 / Initialization state reset")
    }

    init {
        try {
            System.loadLibrary("fmodL")
            System.loadLibrary("fmod")
            System.loadLibrary("fmodstudioL")
            System.loadLibrary("fmodstudio")
            System.loadLibrary("dotnethost")
            System.loadLibrary("FAudio")
            System.loadLibrary("theorafile")
            System.loadLibrary("SDL2")
            System.loadLibrary("main")
            System.loadLibrary("openal32")
            System.loadLibrary("lwjgl_lz4")
            System.loadLibrary("SkiaSharp")
            AppLogger.info(TAG, "Native libraries loaded successfully")
        } catch (t: Throwable) {
            AppLogger.error(TAG, "加载 Native 库失败 / Failed to load native libraries: ${t.message}", t)
        }
    }

    fun getLastErrorMessage(): String {
        return DotNetLauncher.hostfxrLastErrorMsg
    }

    private fun initializeSDLJNI(context: Context) {
        if (isSDLJNIInitialized) return
        try {
            AppLogger.info(TAG, "正在初始化 SDL JNI 环境 / Initializing SDL JNI environment...")
            SDL.setupJNI()
            SDL.setContext(context)
            isSDLJNIInitialized = true
            AppLogger.info(TAG, "SDL JNI 初始化成功 / SDL JNI initialized successfully")
        } catch (t: Throwable) {
            AppLogger.warn(TAG, "SDL JNI 初始化失败 / Failed to initialize SDL JNI: ${t.message}", t)
        }
    }

    fun launchDotNetAssembly(
        assemblyPath: String,
        args: Array<String>,
        enabledPatches: List<Patch>? = null,
        rendererOverride: String? = null,
        gameEnvVars: Map<String, String?> = emptyMap()
    ): Int {
        try {
            AppLogger.info(TAG, "=== 开始启动 .NET 程序集 / Starting .NET Assembly Launch ===")
            AppLogger.info(TAG, "程序集路径 / Assembly path: $assemblyPath")
            AppLogger.info(TAG, "参数 / Args: ${if (args.isNotEmpty()) args.joinToString(" ") else "<none>"}")
            AppLogger.info(TAG, "渲染器覆盖 / Renderer override: ${rendererOverride ?: "<none>"}")

            val assemblyFile = File(assemblyPath)
            if (!assemblyFile.exists()) {
                AppLogger.error(TAG, "程序集文件不存在 / Assembly file does not exist: $assemblyPath")
                return -1
            }

            AppLogger.info(TAG, "程序集存在 / Assembly exists: ${assemblyFile.exists()}")
            AppLogger.info(TAG, "程序集大小 / Assembly size: ${assemblyFile.length()} bytes")
            AppLogger.info(TAG, "程序集父目录 / Assembly parent: ${assemblyFile.parent ?: "<none>"}")

            val appContext: Context = KoinJavaComponent.get(Context::class.java)
            initializeSDLJNI(appContext)

            EnvVarsManager.quickSetEnvVars(
                "PACKAGE_NAME" to appContext.packageName,
                "EXTERNAL_STORAGE_DIRECTORY" to Environment.getExternalStorageDirectory().path
            )

            val workingDir = assemblyFile.parent ?: ""
            AppLogger.debug(TAG, "切换工作目录 / Changing working directory to: $workingDir")
            NativeMethods.chdir(workingDir)

            val dataDir = prepareDataDirectory(assemblyPath)
            val cacheDir = appContext.cacheDir.absolutePath
            AppLogger.info(TAG, "数据目录 / Data directory: $dataDir")
            AppLogger.info(TAG, "缓存目录 / Cache directory: $cacheDir")

            EnvVarsManager.quickSetEnvVars(
                "HOME" to dataDir,
                "XDG_DATA_HOME" to dataDir,
                "XDG_CONFIG_HOME" to dataDir,
                "XDG_CACHE_HOME" to cacheDir,
                "TMPDIR" to cacheDir
            )

            val settings = SettingsAccess
            val startupHooks = if (!enabledPatches.isNullOrEmpty()) {
                PatchManager.constructStartupHooksEnvVar(enabledPatches)
            } else {
                null
            }

            val monoModPath = AssemblyPatcher.getMonoModInstallPath().toString()

            EnvVarsManager.quickSetEnvVars(
                "DOTNET_STARTUP_HOOKS" to startupHooks,
                "MONOMOD_PATH" to monoModPath,
                "SDL_TOUCH_MOUSE_EVENTS" to "1",
                "SDL_TOUCH_MOUSE_MULTITOUCH" to if (settings.isTouchMultitouchEnabled) "1" else "0",
                "RALCORE_MOUSE_RIGHT_STICK" to if (settings.isMouseRightStickEnabled) "1" else null,
                "SDL_AAUDIO_LOW_LATENCY" to if (settings.isSdlAaudioLowLatency) "1" else "0",
                "RAL_AUDIO_BUFFERSIZE" to settings.ralAudioBufferSize?.toString(),
                "RAL_GL_DIAGNOSTICS" to if (
                    settings.isFnaGlPerfDiagnosticsEnabled && settings.isFPSDisplayEnabled
                ) "1" else "0",
                "RAL_GL_DIAG" to null,
                "RAL_GL_PATH" to null,
                "RAL_GL_TIMING" to null,
                "RAL_GL_COUNT_W" to null,
                "RAL_GL_UPLOAD_W" to null,
                "RAL_GL_COUNT_T" to null,
                "RAL_GL_UPLOAD_T" to null,
                "RAL_GL_UPLOAD_PATH" to null,
                "RAL_GL_MAP_WRITES_S" to null,
                "RAL_GL_SUBDATA_WRITES_S" to null,
                "RAL_GL_DRAW_S" to null,
                "RAL_GL_UPLOAD_MB_S" to null,
                "RAL_GL_DRAWS_FRAME" to null,
                "RAL_GL_FRAME_MS" to null,
                "RAL_GL_SWAP_MS" to null,
                "RAL_GL_SLEEP_MS" to null,
                "RAL_GL_MAP_RATIO" to null,
                "RAL_GL_MAP_ENABLED" to null,
            )

            RendererEnvironmentConfigurator.apply(
                context = appContext,
                rendererOverride = rendererOverride
            )

            if (settings.setThreadAffinityToBigCoreEnabled) {
                AppLogger.info(TAG, "设置线程亲和性 / Setting thread affinity to big cores")
                ThreadAffinityManager.setThreadAffinityToBigCores()
            }

            if (gameEnvVars.isNotEmpty()) {
                val availableInterpolations = linkedMapOf(
                    "PACKAGE_NAME" to appContext.packageName,
                    "EXTERNAL_STORAGE_DIRECTORY" to Environment.getExternalStorageDirectory().path,
                    "HOME" to dataDir,
                    "XDG_DATA_HOME" to dataDir,
                    "XDG_CONFIG_HOME" to dataDir,
                    "XDG_CACHE_HOME" to cacheDir,
                    "TMPDIR" to cacheDir,
                    "MONOMOD_PATH" to monoModPath
                ).apply {
                    startupHooks?.let { put("DOTNET_STARTUP_HOOKS", it) }
                }

                val resolvedGameEnvVars = EnvVarsManager.interpolateEnvVars(
                    envVars = gameEnvVars,
                    availableInterpolations = availableInterpolations
                )

                EnvVarsManager.quickSetEnvVars(resolvedGameEnvVars)
                AppLogger.info(TAG, "附加游戏环境变量 / Additional game env vars applied: ${resolvedGameEnvVars.size}")
            }

            AppLogger.info(TAG, "当前渲染器 / Renderer: ${safeEnv("RALCORE_RENDERER", "native")}")
            AppLogger.info(TAG, "当前 EGL / EGL: ${safeEnv("RALCORE_EGL", "system")}")
            AppLogger.info(TAG, "当前 GLES / GLES: ${safeEnv("LIBGL_GLES", "system")}")
            AppLogger.info(TAG, "当前 FNA3D 库 / FNA3D library: ${safeEnv("FNA3D_OPENGL_LIBRARY", "default")}")
            AppLogger.info(TAG, "当前 HOME / HOME: ${safeEnv("HOME", dataDir)}")
            AppLogger.info(TAG, "当前 XDG_DATA_HOME / XDG_DATA_HOME: ${safeEnv("XDG_DATA_HOME", dataDir)}")
            AppLogger.info(TAG, "当前 XDG_CONFIG_HOME / XDG_CONFIG_HOME: ${safeEnv("XDG_CONFIG_HOME", dataDir)}")
            AppLogger.info(TAG, "当前 XDG_CACHE_HOME / XDG_CACHE_HOME: ${safeEnv("XDG_CACHE_HOME", cacheDir)}")
            AppLogger.info(TAG, "当前 TMPDIR / TMPDIR: ${safeEnv("TMPDIR", cacheDir)}")
            AppLogger.info(TAG, "当前 MONOMOD_PATH / MONOMOD_PATH: ${safeEnv("MONOMOD_PATH", monoModPath)}")
            AppLogger.info(TAG, "当前 DOTNET_STARTUP_HOOKS / DOTNET_STARTUP_HOOKS: ${safeEnv("DOTNET_STARTUP_HOOKS", "<none>")}")
            AppLogger.info(TAG, "hostfxr 启动前错误 / hostfxr last error before launch: ${DotNetLauncher.hostfxrLastErrorMsg}")

            val result = DotNetLauncher.hostfxrLaunch(assemblyPath, args)

            AppLogger.info(TAG, "退出代码 / Exit code: $result")
            AppLogger.info(TAG, "hostfxr 启动后错误 / hostfxr last error after launch: ${DotNetLauncher.hostfxrLastErrorMsg}")
            return result

        } catch (t: Throwable) {
            AppLogger.error(TAG, "启动程序集失败 / Failed to launch assembly: $assemblyPath", t)
            AppLogger.error(TAG, "hostfxr 最后错误 / hostfxr last error: ${DotNetLauncher.hostfxrLastErrorMsg}")
            return -1
        }
    }

    @JvmStatic
    fun launchNewDotNetProcess(assemblyPath: String, args: Array<String>, title: String, gameId: String): Int {
        return try {
            AppLogger.info(TAG, "启动新进程 / Launching new process")
            AppLogger.info(TAG, "程序集路径 / Assembly path: $assemblyPath")
            AppLogger.info(TAG, "标题 / Title: $title")
            AppLogger.info(TAG, "游戏 ID / Game ID: $gameId")
            ProcessLauncherService.launch(assemblyPath, args, title, gameId)
            0
        } catch (t: Throwable) {
            AppLogger.error(TAG, "启动新 .NET 进程失败 / Failed to launch new .NET process", t)
            -1
        }
    }

    private fun prepareDataDirectory(assemblyPath: String): String {
        var finalDataDir = File(assemblyPath).parentFile?.absolutePath ?: ""

        try {
            val defaultDataDir = File(
                Environment.getExternalStorageDirectory(),
                DEFAULT_DATA_DIR_NAME
            )

            if (!defaultDataDir.exists()) {
                defaultDataDir.mkdirs()
            }

            val nomediaFile = File(defaultDataDir, ".nomedia")
            if (!nomediaFile.exists()) {
                nomediaFile.createNewFile()
            }

            finalDataDir = defaultDataDir.absolutePath
            AppLogger.info(TAG, "使用默认数据目录 / Using default data directory: $finalDataDir")
        } catch (t: Throwable) {
            AppLogger.warn(
                TAG,
                "无法访问默认数据目录 / Failed to access default data directory, using fallback: $finalDataDir",
                t
            )
        }

        return finalDataDir
    }

    private fun safeEnv(key: String, fallback: String): String {
        return try {
            Os.getenv(key) ?: fallback
        } catch (_: Throwable) {
            fallback
        }
    }
}
