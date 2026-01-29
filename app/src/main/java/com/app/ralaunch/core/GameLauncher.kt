/**
 * 游戏启动器模块
 * Game Launcher Module
 *
 * 本模块是应用的核心组件，负责启动和管理不同类型的游戏进程。
 * This module is the core component of the application, responsible for launching
 * and managing different types of game processes.
 *
 * 支持的游戏类型：
 * Supported game types:
 * - .NET/FNA 游戏（通过 CoreCLR 运行时）
 *   .NET/FNA games (via CoreCLR runtime)
 * - Box64 x86_64 Linux 游戏（通过 Box64 仿真层）
 *   Box64 x86_64 Linux games (via Box64 emulation layer)
 *
 * @see DotNetLauncher .NET 运行时启动器 / .NET runtime launcher
 * @see Box64Helper Box64 仿真层辅助类 / Box64 emulation layer helper
 */
package com.app.ralaunch.core

import android.content.Context
import android.os.Environment
import android.system.Os
import com.app.ralaunch.data.SettingsManager
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.dotnet.DotNetLauncher
import com.app.ralaunch.utils.AppLogger
import com.app.ralaunch.utils.NativeMethods
import com.app.ralaunch.renderer.RendererConfig
import com.app.ralaunch.patch.Patch
import com.app.ralaunch.patch.PatchManager
import com.app.ralaunch.box64.Box64Helper
import com.app.ralaunch.box64.GlibcBox64Helper
import com.app.ralaunch.box64.NativeBridge
import com.app.ralaunch.runtime.RuntimeLibraryLoader
import com.app.ralaunch.service.ProcessLauncherService
import kotlinx.coroutines.runBlocking
import org.libsdl.app.SDL
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists

/**
 * 游戏启动器 - 统一管理游戏启动流程
 * Game Launcher - Unified game launch process management
 *
 * 提供以下核心功能：
 * Provides the following core features:
 * - 启动 .NET/FNA 游戏并配置运行时环境
 *   Launch .NET/FNA games with runtime environment configuration
 * - 启动 Box64 仿真的 x86_64 Linux 游戏
 *   Launch Box64-emulated x86_64 Linux games
 * - 管理游戏数据目录和环境变量
 *   Manage game data directories and environment variables
 * - 配置补丁和启动钩子
 *   Configure patches and startup hooks
 */
object GameLauncher {

    private const val TAG = "GameLauncher"

    /**
     * 默认数据目录名称
     * Default data directory name
     */
    private const val DEFAULT_DATA_DIR_NAME = "RALauncher"

    /**
     * Box64 环境是否已初始化
     * Whether Box64 environment is initialized
     */
    private var isBox64Initialized = false

    /**
     * SDL JNI 环境是否已初始化
     * Whether SDL JNI environment is initialized
     */
    private var isSDLJNIInitialized = false

    /**
     * 最后一次 Box64 错误消息
     * Last Box64 error message
     */
    @Volatile
    private var lastBox64ErrorMessage: String = ""

    /**
     * 重置初始化状态
     * Reset initialization state
     *
     * 在每次启动新游戏前调用，确保环境被正确重新初始化
     * Called before launching a new game to ensure environment is properly re-initialized
     */
    fun resetInitializationState() {
        isBox64Initialized = false
        isSDLJNIInitialized = false
        AppLogger.info(TAG, "初始化状态已重置 / Initialization state reset")
    }

    /**
     * 静态初始化块 - 加载所有必需的 Native 库
     * Static initializer - Load all required native libraries
     *
     * 加载顺序很重要，某些库依赖于其他库。
     * Loading order matters, some libraries depend on others.
     *
     * 包含的库：
     * Included libraries:
     * - FMOD: 音频引擎（Celeste 等游戏需要）/ Audio engine (required by games like Celeste)
     * - SDL2: 跨平台多媒体库 / Cross-platform multimedia library
     * - FAudio: XAudio2 替代实现 / XAudio2 reimplementation
     * - SkiaSharp: 2D 图形库 / 2D graphics library
     */
    init {
        try {
            // FMOD 音频库（某些游戏如 Celeste 需要）
            // FMOD audio libraries (required by some games like Celeste)
            System.loadLibrary("fmodL")
            System.loadLibrary("fmod")
            System.loadLibrary("fmodstudioL")
            System.loadLibrary("fmodstudio")

            // 核心运行时库（必须打包在 APK 中）
            // Core runtime libraries (must be bundled in APK)
            System.loadLibrary("netcorehost")
            System.loadLibrary("FAudio")
            System.loadLibrary("theorafile")
            System.loadLibrary("SDL2")
            System.loadLibrary("main")

            // Box64 仿真层需要的 glibc 桥接库
            // glibc bridge library required by Box64 emulation layer
            System.loadLibrary("glibc_bridge")

            System.loadLibrary("openal32")
            
           
           
            // - libbox64.so (56 MB) - 通过 Box64Helper.ensureBox64Loaded() 加载
            // - libSkiaSharp.so (7 MB) - 通过 RuntimeLibraryLoader.loadSkiaSharp() 加载
            // - libvulkan_freedreno.so (10 MB) - 通过 RuntimeLibraryLoader.loadRendererLibraries() 加载
            // - libGL_gl4es.so (4 MB) - 通过 RuntimeLibraryLoader.loadRendererLibraries() 加载
            // - lib7-Zip-JBinding.so (3 MB) - 通过 RuntimeLibraryLoader.load7Zip() 加载
        } catch (e: UnsatisfiedLinkError) {
            AppLogger.error(TAG, "加载 Native 库失败 / Failed to load native libraries: ${e.message}")
        }
    }

    /**
     * 获取最后一次错误信息
     * Get the last error message
     *
     * 目前仅返回 .NET 运行时的错误信息，未来可能扩展支持其他错误来源。
     * Currently only returns .NET runtime error messages, may be extended
     * to support other error sources in the future.
     *
     * @return 错误信息字符串，如果没有错误则返回空字符串
     *         Error message string, or empty string if no error
     */
    fun getLastErrorMessage(): String {
        // 优先返回 Box64 错误（如果有）
        if (lastBox64ErrorMessage.isNotEmpty()) {
            return lastBox64ErrorMessage
        }
        return DotNetLauncher.hostfxrLastErrorMsg
    }

    /**
     * 设置 Box64 错误消息
     * Set Box64 error message
     */
    private fun setBox64Error(message: String) {
        lastBox64ErrorMessage = message
        AppLogger.error(TAG, "Box64 Error: $message")
    }

    /**
     * 清除 Box64 错误消息
     * Clear Box64 error message
     */
    private fun clearBox64Error() {
        lastBox64ErrorMessage = ""
    }

    /**
     * 启动 Box64 游戏
     * Launch a Box64 game
     *
     * 用于运行 x86_64 Linux 游戏（如 Starbound）。
     * Box64 会将 x86_64 指令翻译为 ARM64 指令执行。
     *
     * Used to run x86_64 Linux games (like Starbound).
     * Box64 translates x86_64 instructions to ARM64 for execution.
     *
     * 启动流程：
     * Launch process:
     * 1. 初始化 Box64 环境（提取 rootfs）
     *    Initialize Box64 environment (extract rootfs)
     * 2. 配置渲染器环境变量
     *    Configure renderer environment variables
     * 3. 初始化 SDL JNI 环境
     *    Initialize SDL JNI environment
     * 4. 通过 Box64Helper 启动游戏
     *    Launch game via Box64Helper
     *
     * @param context Android 上下文，用于访问应用资源
     *                Android context for accessing app resources
     * @param gamePath 游戏可执行文件的完整路径
     *                 Full path to the game executable
     * @return 游戏退出代码：
     *         Game exit code:
     *         - 0 或正数：正常退出码 / Normal exit code
     *         - -1：Box64 初始化失败 / Box64 initialization failed
     *         - -2：无法获取游戏目录 / Failed to get game directory
     *         - -3：启动过程中发生异常 / Exception during launch
     */
    fun launchBox64Game(context: Context, gamePath: String): Int {
        // 清除之前的错误信息
        clearBox64Error()
        
        try {
            AppLogger.info(TAG, "=== 开始启动 Box64 游戏 / Starting Box64 Game Launch ===")
            AppLogger.info(TAG, "游戏路径 / Game path: $gamePath")

            // 步骤1：初始化 Box64 环境
            // Step 1: Initialize Box64 environment
            if (!initializeBox64(context)) {
                // 错误信息已在 initializeBox64 中设置
                return -1
            }

            // 步骤2：配置渲染器环境变量（必须在 Box64 启动前设置）
            // Step 2: Configure renderer environment (must be set before Box64 starts)
            AppLogger.debug(TAG, "配置渲染器环境 / Applying renderer environment...")
            RendererConfig.applyRendererEnvironment(context)
            AppLogger.debug(TAG, "渲染器环境配置完成 / Renderer environment applied: OK")

            // 步骤3：配置音频环境变量
            // Step 3: Configure audio environment variables
            val settings = SettingsManager.getInstance()
            EnvVarsManager.quickSetEnvVars(
                "SDL_AAUDIO_LOW_LATENCY" to if (settings.isSdlAaudioLowLatency) "1" else "0",
            )

            // 步骤4：初始化 SDL JNI 环境
            // Step 4: Initialize SDL JNI environment
            initializeSDLJNI(context)

            val filesDir = context.filesDir.absolutePath
            val rootfsPath = "$filesDir/rootfs"
            val gameDir = File(gamePath).parent
            if (gameDir == null) {
                setBox64Error("无法获取游戏目录，游戏路径可能无效: $gamePath")
                return -2
            }

            // 设置 Box64 的 rootfs 路径
            // Set Box64 rootfs path
            System.setProperty("BOX64_ROOTFS", rootfsPath)

            AppLogger.info(TAG, "通过 Box64 启动游戏 / Launching via Box64...")
            AppLogger.info(TAG, "  Rootfs 路径: $rootfsPath")
            AppLogger.info(TAG, "  工作目录 / Work dir: $gameDir")

            // 步骤5：执行游戏
            // Step 5: Execute the game
            val result = Box64Helper.runBox64InProcess(arrayOf(gamePath), gameDir)

            AppLogger.info(TAG, "=== Box64 游戏启动完成 / Box64 Game Launch Completed ===")
            AppLogger.info(TAG, "退出代码 / Exit code: $result")

            // 根据退出码设置错误信息
            if (result != 0) {
                setBox64Error(getBox64ExitCodeDescription(result))
            }

            return result
        } catch (e: Exception) {
            AppLogger.error(TAG, "启动 Box64 游戏失败 / Failed to launch Box64 game: $gamePath", e)
            setBox64Error("启动异常: ${e.message}\n${e.stackTraceToString().take(500)}")
            return -3
        }
    }

    /**
     * 通过 glibc_bridge 启动 Box64 游戏（glibc 版本）
     * Launch a Box64 game via glibc_bridge (glibc version)
     *
     * 使用 glibc 编译的 Box64，通过 glibc_bridge 执行。
     * 与 launchBox64Game() 的区别：
     * - launchBox64Game: 使用 bionic 编译的 Box64 共享库，通过 dlopen 加载
     * - launchBox64ViaGlibc: 使用 glibc 编译的 Box64 可执行文件，通过 glibc_bridge 执行
     *
     * @param context Android 上下文
     * @param gamePath 游戏可执行文件的完整路径
     * @return 游戏退出代码
     */
    fun launchBox64ViaGlibc(context: Context, gamePath: String): Int {
        clearBox64Error()
        
        try {
            AppLogger.info(TAG, "=== 开始启动 Box64 游戏 (glibc 模式) ===")
            AppLogger.info(TAG, "游戏路径: $gamePath")
            
            // 步骤1：确保 glibc Box64 已解压
            if (!GlibcBox64Helper.isExtracted(context)) {
                AppLogger.info(TAG, "正在解压 Box64 glibc...")
                val extracted = runBlocking { GlibcBox64Helper.extractBox64(context) }
                if (!extracted) {
                    setBox64Error("Box64 glibc 解压失败")
                    return -1
                }
            }
            
            // 步骤2：确保 rootfs 已解压（包含 glibc 运行时库）
            val rootfsPath = "${context.filesDir.absolutePath}/rootfs"
            if (!File(rootfsPath).exists()) {
                // 初始化 glibc_bridge（会处理 rootfs）
                NativeBridge.loadLibrary()
                val initResult = NativeBridge.init(context, context.filesDir.absolutePath)
                if (initResult != 0) {
                    setBox64Error("glibc_bridge 初始化失败: $initResult")
                    return -1
                }
            }
            
            // 步骤3：配置渲染器环境变量
            AppLogger.debug(TAG, "配置渲染器环境...")
            RendererConfig.applyRendererEnvironment(context)
            
            // 步骤4：初始化 SDL JNI 环境
            initializeSDLJNI(context)
            
            val gameDir = File(gamePath).parent
            if (gameDir == null) {
                setBox64Error("无法获取游戏目录: $gamePath")
                return -2
            }
            
            AppLogger.info(TAG, "通过 glibc_bridge 启动 Box64...")
            AppLogger.info(TAG, "  Box64 路径: ${GlibcBox64Helper.getBox64Path(context)}")
            AppLogger.info(TAG, "  工作目录: $gameDir")
            
            // 步骤5：通过 glibc_bridge 执行
            val result = GlibcBox64Helper.runBox64(
                context = context,
                args = arrayOf(gamePath),
                workDir = gameDir
            )
            
            AppLogger.info(TAG, "=== Box64 游戏启动完成 (glibc 模式) ===")
            AppLogger.info(TAG, "退出代码: $result")
            
            if (result != 0) {
                setBox64Error(getBox64ExitCodeDescription(result))
            }
            
            return result
        } catch (e: Exception) {
            AppLogger.error(TAG, "启动 Box64 游戏失败 (glibc 模式)", e)
            setBox64Error("启动异常: ${e.message}")
            return -3
        }
    }

    /**
     * 获取 Box64 退出码的描述
     */
    private fun getBox64ExitCodeDescription(exitCode: Int): String {
        return when (exitCode) {
            -1 -> "Box64 初始化失败（运行时库未就绪或加载失败）"
            -2 -> "Box64 库未加载，请确保 runtime_libs 已正确解压"
            -3 -> "Box64 启动过程中发生异常"
            1 -> "游戏异常退出（通用错误）"
            127 -> "找不到游戏可执行文件或缺少依赖库"
            134 -> "游戏崩溃 (SIGABRT)"
            136 -> "浮点异常 (SIGFPE)"
            137 -> "进程被杀死 (SIGKILL)"
            139 -> "段错误 (SIGSEGV) - 内存访问错误"
            else -> "游戏退出，代码: $exitCode"
        }
    }

    /**
     * 初始化 Box64 仿真环境
     * Initialize Box64 emulation environment
     *
     * 此方法会提取 rootfs 并初始化 Box64 运行时环境。
     * 可在游戏安装时预先调用，避免首次启动时的延迟。
     *
     * This method extracts rootfs and initializes Box64 runtime environment.
     * Can be called during game installation to avoid delay on first launch.
     *
     * @param context Android 上下文，用于访问应用私有目录
     *                Android context for accessing app private directory
     * @return 初始化是否成功
     *         Whether initialization succeeded
     */
    fun initializeBox64(context: Context): Boolean {
        // 避免重复初始化
        // Avoid repeated initialization
        if (isBox64Initialized) return true

        try {
            AppLogger.info(TAG, "正在初始化 Box64 环境 / Initializing Box64 environment...")

            // 检查 assets 中是否存在 runtime_libs.tar.xz
            val hasRuntimeLibsAsset = try {
                context.assets.list("")?.contains("runtime_libs.tar.xz") == true
            } catch (e: Exception) {
                false
            }

            // 确保运行时库已解压
            // Ensure runtime libraries are extracted
            if (!RuntimeLibraryLoader.isExtracted(context)) {
                if (!hasRuntimeLibsAsset) {
                    val runtimeDir = RuntimeLibraryLoader.getRuntimeLibsDir(context)
                    setBox64Error(buildString {
                        append("运行时库未找到\n")
                        append("- assets 中不存在 runtime_libs.tar.xz\n")
                        append("- 运行时目录: ${runtimeDir.absolutePath}\n")
                        append("- 目录存在: ${runtimeDir.exists()}\n")
                        if (runtimeDir.exists()) {
                            val files = runtimeDir.listFiles()?.map { it.name } ?: emptyList()
                            append("- 目录内容: ${files.take(10).joinToString(", ")}")
                            if (files.size > 10) append("... (共${files.size}个文件)")
                        }
                    })
                    return false
                }
                
                AppLogger.info(TAG, "运行时库未解压，正在解压... / Runtime libs not extracted, extracting...")
                val extracted = runBlocking {
                    RuntimeLibraryLoader.extractRuntimeLibs(context) { progress, message ->
                        AppLogger.debug(TAG, "解压进度 / Extract progress: $progress% - $message")
                    }
                }
                if (!extracted) {
                    setBox64Error("运行时库解压失败，请检查存储空间是否充足")
                    return false
                }
                AppLogger.info(TAG, "运行时库解压完成 / Runtime libraries extracted successfully")
            }

            val filesDir = context.filesDir.absolutePath
            val result = NativeBridge.init(context, filesDir)

            if (result != 0) {
                setBox64Error("glibc_bridge 初始化失败，错误码: $result\n可能原因：rootfs 不完整或权限问题")
                return false
            }
            AppLogger.info(TAG, "glibc_bridge 初始化成功 / glibc_bridge initialized successfully")

            // 加载 Box64 库
            // Load Box64 library
            val box64Path = Box64Helper.getBox64LibPath(context)
            val box64File = File(box64Path)
            if (!box64File.exists()) {
                setBox64Error(buildString {
                    append("Box64 库文件不存在\n")
                    append("- 期望路径: $box64Path\n")
                    append("- 请确保 runtime_libs.tar.xz 中包含 libbox64.so")
                })
                return false
            }
            
            if (!Box64Helper.ensureBox64Loaded(context)) {
                setBox64Error(buildString {
                    append("Box64 库加载失败\n")
                    append("- 库路径: $box64Path\n")
                    append("- 文件大小: ${box64File.length() / 1024 / 1024} MB\n")
                    append("- 可能原因: 库文件损坏、ABI 不兼容或内存不足")
                })
                return false
            }

            isBox64Initialized = true
            AppLogger.info(TAG, "Box64 初始化成功 / Box64 initialized successfully")
            return true
        } catch (e: Exception) {
            AppLogger.error(TAG, "Box64 初始化异常 / Failed to initialize Box64", e)
            setBox64Error("Box64 初始化异常: ${e.message}")
            return false
        }
    }

    /**
     * 初始化 SDL JNI 环境
     * Initialize SDL JNI environment
     *
     * 重要：此方法只设置 JNI 环境和 Context，不会调用 SDL.initialize()。
     * Important: This method only sets up JNI environment and Context,
     * it does NOT call SDL.initialize().
     *
     * 原因：当从 GameActivity（继承自 SDLActivity）启动时，
     * SDLActivity.onCreate() 已经初始化了 SDL。
     * 再次调用 SDL.initialize() 会重置 mSingleton 和 mSurface 为 null，
     * 导致 Box64 的 SDL_CreateWindow() 无法获取 native window。
     *
     * Reason: When launched from GameActivity (extends SDLActivity),
     * SDLActivity.onCreate() has already initialized SDL.
     * Calling SDL.initialize() again would reset mSingleton and mSurface to null,
     * causing Box64's SDL_CreateWindow() to fail to get native window.
     *
     * @param context Android 上下文，供 SDL 音频系统使用
     *                Android context for SDL audio system
     */
    private fun initializeSDLJNI(context: Context) {
        if (isSDLJNIInitialized) return

        try {
            AppLogger.info(TAG, "正在初始化 SDL JNI 环境 / Initializing SDL JNI environment...")
            SDL.setupJNI()

            // 只设置 Context，不调用 initialize()
            // Only set Context, do NOT call initialize()
            SDL.setContext(context)
            isSDLJNIInitialized = true

            AppLogger.info(TAG, "SDL JNI 初始化成功（未重新初始化 SDLActivity）/ SDL JNI initialized successfully (without re-initializing SDLActivity)")
        } catch (e: Exception) {
            AppLogger.warn(TAG, "SDL JNI 初始化失败 / Failed to initialize SDL JNI: ${e.message}")
        }
    }

    /**
     * 启动 .NET 程序集
     * Launch a .NET assembly
     *
     * 此方法负责准备游戏运行环境并调用 .NET 运行时启动游戏。
     * 只处理游戏相关的环境配置，底层运行时环境变量由 DotNetLauncher 处理。
     *
     * This method prepares the game runtime environment and calls .NET runtime to launch the game.
     * Only handles game-related environment configuration, low-level runtime environment
     * variables are handled by DotNetLauncher.
     *
     * 启动流程：
     * Launch process:
     * 1. 验证程序集文件存在
     *    Verify assembly file exists
     * 2. 设置环境变量（包名、存储路径等）
     *    Set environment variables (package name, storage path, etc.)
     * 3. 切换工作目录到程序集所在目录
     *    Change working directory to assembly location
     * 4. 准备数据目录（HOME、XDG_* 等）
     *    Prepare data directories (HOME, XDG_*, etc.)
     * 5. 配置补丁和启动钩子
     *    Configure patches and startup hooks
     * 6. 配置渲染器和线程亲和性
     *    Configure renderer and thread affinity
     * 7. 调用 hostfxr 启动 .NET 运行时
     *    Call hostfxr to launch .NET runtime
     *
     * @param assemblyPath 程序集（.dll）的完整路径
     *                     Full path to the assembly (.dll)
     * @param args 传递给程序集的命令行参数
     *             Command line arguments to pass to the assembly
     * @param enabledPatches 要启用的补丁列表，null 表示不使用补丁
     *                       List of patches to enable, null means no patches
     * @return 程序集退出代码：
     *         Assembly exit code:
     *         - 0 或正数：正常退出码 / Normal exit code
     *         - -1：启动失败（文件不存在或发生异常）/ Launch failed (file not found or exception)
     */
    fun launchDotNetAssembly(assemblyPath: String, args: Array<String>, enabledPatches: List<Patch>? = null): Int {
        try {
            AppLogger.info(TAG, "=== 开始启动 .NET 程序集 / Starting .NET Assembly Launch ===")
            AppLogger.info(TAG, "程序集路径 / Assembly path: $assemblyPath")
            AppLogger.info(TAG, "启动参数 / Arguments: ${args.joinToString(", ")}")
            AppLogger.info(TAG, "启用补丁数 / Enabled patches: ${enabledPatches?.size ?: 0}")

            // 步骤1：验证程序集文件存在
            // Step 1: Verify assembly file exists
            if (!Path(assemblyPath).exists()) {
                AppLogger.error(TAG, "程序集文件不存在 / Assembly file does not exist: $assemblyPath")
                return -1
            }
            AppLogger.debug(TAG, "程序集文件验证通过 / Assembly file exists: OK")

            // 步骤2：设置基础环境变量
            // Step 2: Set basic environment variables
            AppLogger.debug(TAG, "设置环境变量 / Setting up environment variables...")
            val appContext: Context = KoinJavaComponent.get(Context::class.java)
            EnvVarsManager.quickSetEnvVars(
                "PACKAGE_NAME" to appContext.packageName,
                "EXTERNAL_STORAGE_DIRECTORY" to Environment.getExternalStorageDirectory().path
            )

            // 步骤3：切换工作目录
            // Step 3: Change working directory
            val workingDir = Path(assemblyPath).parent.toString()
            AppLogger.debug(TAG, "切换工作目录 / Changing working directory to: $workingDir")
            NativeMethods.chdir(workingDir)
            AppLogger.debug(TAG, "工作目录切换完成 / Working directory changed: OK")

            // 步骤4：准备数据目录
            // Step 4: Prepare data directory
            AppLogger.debug(TAG, "准备数据目录 / Preparing data directory...")
            val dataDir = prepareDataDirectory(assemblyPath)
            val cacheDir = appContext.cacheDir.absolutePath
            AppLogger.info(TAG, "数据目录 / Data directory: $dataDir")

            EnvVarsManager.quickSetEnvVars(
                "HOME" to dataDir,
                "XDG_DATA_HOME" to dataDir,
                "XDG_CONFIG_HOME" to dataDir,
                "XDG_CACHE_HOME" to cacheDir
            )
            AppLogger.debug(TAG, "XDG 环境变量设置完成 / XDG environment variables set: OK")

            // 步骤5：应用用户设置
            // Step 5: Apply user settings
            val settings = SettingsManager.getInstance()
            AppLogger.debug(TAG, "应用用户设置 / Applying settings configuration...")
            AppLogger.debug(TAG, "  - 大核亲和性 / Big core affinity: ${settings.setThreadAffinityToBigCoreEnabled}")
            AppLogger.debug(TAG, "  - 多点触控 / Touch multitouch: ${settings.isTouchMultitouchEnabled}")
            AppLogger.debug(TAG, "  - 鼠标右摇杆 / Mouse right stick: ${settings.isMouseRightStickEnabled}")

            // 步骤6：配置启动钩子（补丁）
            // Step 6: Configure startup hooks (patches)
            val startupHooks = if (enabledPatches != null && enabledPatches.isNotEmpty())
                PatchManager.constructStartupHooksEnvVar(enabledPatches) else null

            if (startupHooks != null) {
                AppLogger.info(TAG, "已配置 ${enabledPatches!!.size} 个补丁的启动钩子 / DOTNET_STARTUP_HOOKS configured with ${enabledPatches.size} patch(es)")
                AppLogger.debug(TAG, "DOTNET_STARTUP_HOOKS 值 / value: $startupHooks")
                val hookCount = startupHooks.split(":").filter { it.isNotEmpty() }.size
                AppLogger.debug(TAG, "实际钩子数量 / Actual hook count: $hookCount")
            } else {
                AppLogger.debug(TAG, "未配置启动钩子 / No startup hooks configured")
            }

            // 步骤7：设置 MonoMod 路径（供补丁使用）
            // Step 7: Set MonoMod path (for patches)
            val monoModPath = AssemblyPatcher.getMonoModInstallPath().toString()
            AppLogger.info(TAG, "MonoMod 路径 / path: $monoModPath")

            // 模组路径配置（外部存储 RALauncher 目录）
            // Mod paths configuration (RALauncher directory in external storage)
            val smapiModsPath = Path(dataDir).resolve("Stardew Valley").resolve("Mods")
            val everestModsPath = Path(dataDir).resolve("Everest").resolve("Mods")
            
            // 创建模组目录（如果不存在）
            // Create mod directories if they don't exist
            try {
                if (!smapiModsPath.exists()) {
                    smapiModsPath.createDirectories()
                    AppLogger.debug(TAG, "创建 SMAPI 模组目录 / Created SMAPI mods directory: $smapiModsPath")
                }
                if (!everestModsPath.exists()) {
                    everestModsPath.createDirectories()
                    AppLogger.debug(TAG, "创建 Everest 模组目录 / Created Everest mods directory: $everestModsPath")
                }
            } catch (e: Exception) {
                AppLogger.warn(TAG, "无法创建模组目录 / Failed to create mod directories: ${e.message}")
            }
            
            EnvVarsManager.quickSetEnvVars(
                // 启动钩子配置
                // Startup hooks configuration
                "DOTNET_STARTUP_HOOKS" to startupHooks,

                // MonoMod 路径，供补丁的 AssemblyResolve 使用
                // MonoMod path, used by patch's AssemblyResolve
                "MONOMOD_PATH" to monoModPath,

                // SMAPI 模组路径（星露谷物语）
                // SMAPI mods path (Stardew Valley)
                "SMAPI_MODS_PATH" to smapiModsPath.toString(),
                
                // Everest 模组路径（蔚蓝）- 通过 XDG_DATA_HOME 自动设置
                // Everest 使用 XDG_DATA_HOME/Everest/Mods，已在上方设置 XDG_DATA_HOME
                // Everest mods path (Celeste) - auto-configured via XDG_DATA_HOME
                // Everest uses XDG_DATA_HOME/Everest/Mods, XDG_DATA_HOME is set above

                // 触摸输入配置
                // Touch input configuration
                "SDL_TOUCH_MOUSE_EVENTS" to "1",
                "SDL_TOUCH_MOUSE_MULTITOUCH" to if (settings.isTouchMultitouchEnabled) "1" else "0",
                "RALCORE_MOUSE_RIGHT_STICK" to if (settings.isMouseRightStickEnabled) "1" else null,

                // 音频配置
                // Audio configuration
                "SDL_AAUDIO_LOW_LATENCY" to if (settings.isSdlAaudioLowLatency) "1" else "0",
            )
            AppLogger.info(TAG, "SMAPI 模组路径 / SMAPI mods path: $smapiModsPath")
            AppLogger.info(TAG, "Everest 模组路径 / Everest mods path: $everestModsPath")
            AppLogger.debug(TAG, "游戏设置环境变量配置完成 / Game settings environment variables set: OK")

            // 步骤8：配置渲染器
            // Step 8: Configure renderer
            AppLogger.debug(TAG, "配置渲染器环境 / Applying renderer environment...")
            RendererConfig.applyRendererEnvironment(appContext)
            AppLogger.debug(TAG, "渲染器环境配置完成 / Renderer environment applied: OK")

            // 步骤9：设置线程亲和性
            // Step 9: Set thread affinity
            if (settings.setThreadAffinityToBigCoreEnabled) {
                AppLogger.debug(TAG, "设置线程亲和性到大核 / Setting thread affinity to big cores...")
                val result = ThreadAffinityManager.setThreadAffinityToBigCores()
                AppLogger.debug(TAG, "线程亲和性设置完成 / Thread affinity to big cores set: Result=$result")
            } else {
                AppLogger.debug(TAG, "未启用大核亲和性，跳过 / Thread affinity to big cores not enabled, skipping.")
            }

            // 步骤10：启动 .NET 运行时
            // Step 10: Launch .NET runtime
            AppLogger.info(TAG, "通过 hostfxr 启动 .NET 运行时 / Launching .NET runtime with hostfxr...")
            val result = DotNetLauncher.hostfxrLaunch(assemblyPath, args)

            AppLogger.info(TAG, "=== .NET 程序集启动完成 / .NET Assembly Launch Completed ===")
            AppLogger.info(TAG, "退出代码 / Exit code: $result")

            return result
        } catch (e: Exception) {
            AppLogger.error(TAG, "启动程序集失败 / Failed to launch assembly: $assemblyPath", e)
            e.printStackTrace()
            return -1
        }
    }

    /**
     * 在新进程中启动 .NET 程序集
     * Launch a .NET assembly in a new process
     *
     * 此方法由 Native 层调用，用于在独立进程中启动子程序集。
     * 例如：某些游戏可能需要启动额外的工具或服务器进程。
     *
     * This method is called from native layer to launch sub-assemblies in separate processes.
     * For example: some games may need to launch additional tools or server processes.
     *
     * @param assemblyPath 程序集的完整路径
     *                     Full path to the assembly
     * @param args 传递给程序集的命令行参数
     *             Command line arguments for the assembly
     * @param title 进程标题，用于日志和调试
     *              Process title for logging and debugging
     * @param gameId 游戏标识符，用于匹配相关补丁
     *               Game identifier for matching related patches
     * @return 启动结果：0 表示成功，-1 表示失败
     *         Launch result: 0 for success, -1 for failure
     */
    @JvmStatic
    fun launchNewDotNetProcess(assemblyPath: String, args: Array<String>, title: String, gameId: String): Int {
        try {
            AppLogger.info(TAG, "=== 收到新进程启动请求 / launchNewDotNetProcess called ===")
            AppLogger.info(TAG, "程序集 / Assembly: $assemblyPath")
            AppLogger.info(TAG, "标题 / Title: $title")
            AppLogger.info(TAG, "游戏ID / Game ID: $gameId")
            AppLogger.info(TAG, "参数 / Arguments: ${args.joinToString(", ")}")

            ProcessLauncherService.launch(assemblyPath, args, title, gameId)

            return 0
        } catch (e: Exception) {
            AppLogger.error(TAG, "启动新 .NET 进程失败 / Failed to launch new .NET process", e)
            return -1
        }
    }

    /**
     * 准备游戏数据目录
     * Prepare game data directory
     *
     * 创建并返回游戏存档、配置等数据的存储目录。
     * 默认使用外部存储的 RALauncher 目录，如果无法访问则回退到程序集所在目录。
     *
     * Creates and returns the storage directory for game saves, configs, etc.
     * Defaults to RALauncher directory in external storage,
     * falls back to assembly directory if inaccessible.
     *
     * 目录结构：
     * Directory structure:
     * - /storage/emulated/0/RALauncher/
     *   - .nomedia（防止媒体扫描 / Prevents media scanning）
     *   - [游戏存档和配置 / Game saves and configs]
     *
     * @param assemblyPath 程序集路径，用于获取回退目录
     *                     Assembly path for fallback directory
     * @return 数据目录的绝对路径
     *         Absolute path to the data directory
     */
    private fun prepareDataDirectory(assemblyPath: String): String {
        // 初始回退目录为程序集所在目录
        // Initial fallback is the assembly's parent directory
        var finalDataDir = Path(assemblyPath).parent
        AppLogger.debug(TAG, "初始数据目录（程序集父目录）/ Initial data directory (assembly parent): $finalDataDir")

        try {
            // 尝试使用外部存储的默认数据目录
            // Try to use default data directory in external storage
            val defaultDataDirPath = android.os.Environment.getExternalStorageDirectory()
                .resolve(DEFAULT_DATA_DIR_NAME)
                .toPath()

            AppLogger.debug(TAG, "目标数据目录 / Target data directory: $defaultDataDirPath")

            // 创建目录（如果不存在）
            // Create directory if it doesn't exist
            if (!defaultDataDirPath.exists()) {
                AppLogger.debug(TAG, "创建数据目录 / Creating data directory: $defaultDataDirPath")
                defaultDataDirPath.createDirectories()
                AppLogger.debug(TAG, "数据目录创建成功 / Data directory created: OK")
            } else {
                AppLogger.debug(TAG, "数据目录已存在 / Data directory already exists")
            }

            // 创建 .nomedia 文件防止媒体扫描
            // Create .nomedia file to prevent media scanning
            val nomediaFilePath = defaultDataDirPath.resolve(".nomedia")
            if (!nomediaFilePath.exists()) {
                AppLogger.debug(TAG, "创建 .nomedia 文件 / Creating .nomedia file: $nomediaFilePath")
                nomediaFilePath.createFile()
                AppLogger.debug(TAG, ".nomedia 文件创建成功 / .nomedia file created: OK")
            } else {
                AppLogger.debug(TAG, ".nomedia 文件已存在 / .nomedia file already exists")
            }

            finalDataDir = defaultDataDirPath
            AppLogger.info(TAG, "使用默认数据目录 / Using default data directory: $finalDataDir")
        } catch (e: Exception) {
            // 无法访问外部存储，使用程序集目录作为回退
            // Cannot access external storage, use assembly directory as fallback
            AppLogger.warn(TAG, "无法访问默认数据目录，使用程序集目录 / Failed to access default data directory, using assembly directory instead.", e)
            AppLogger.warn(TAG, "回退数据目录 / Fallback data directory: $finalDataDir")
        }

        return finalDataDir.toString()
    }
}
