package com.app.ralaunch.shared.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.app.ralaunch.shared.data.local.PreferencesKeys
import com.app.ralaunch.shared.domain.model.AppSettings
import com.app.ralaunch.shared.domain.model.BackgroundType
import com.app.ralaunch.shared.domain.model.FnaRenderer
import com.app.ralaunch.shared.domain.model.KeyboardType
import com.app.ralaunch.shared.domain.model.ThemeMode
import com.app.ralaunch.shared.domain.repository.SettingsRepositoryV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 设置仓库实现（V2）
 */
class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : SettingsRepositoryV2 {

    private val writeMutex = Mutex()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val settings: StateFlow<AppSettings> = dataStore.data
        .map(::toAppSettings)
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings.Default
        )

    override suspend fun getSettingsSnapshot(): AppSettings = toAppSettings(dataStore.data.first())

    override suspend fun updateSettings(settings: AppSettings) {
        writeMutex.withLock {
            dataStore.edit { prefs ->
                writeAppSettings(prefs, settings)
            }
        }
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        writeMutex.withLock {
            dataStore.edit { prefs ->
                val updated = transform(toAppSettings(prefs))
                writeAppSettings(prefs, updated)
            }
        }
    }

    override suspend fun resetToDefaults() {
        writeMutex.withLock {
            dataStore.edit { prefs ->
                prefs.clear()
            }
        }
    }

    private fun toAppSettings(prefs: Preferences): AppSettings {
        val defaults = AppSettings.Default
        val normalizedRenderer = normalizeRendererValue(prefs[PreferencesKeys.FNA_RENDERER])
        return AppSettings(
            themeMode = ThemeMode.fromValue(prefs[PreferencesKeys.THEME_MODE] ?: defaults.themeMode.value),
            themeColor = prefs[PreferencesKeys.THEME_COLOR] ?: defaults.themeColor,
            backgroundType = BackgroundType.fromValue(prefs[PreferencesKeys.BACKGROUND_TYPE] ?: defaults.backgroundType.value),
            backgroundColor = prefs[PreferencesKeys.BACKGROUND_COLOR] ?: defaults.backgroundColor,
            backgroundImagePath = prefs[PreferencesKeys.BACKGROUND_IMAGE_PATH] ?: defaults.backgroundImagePath,
            backgroundVideoPath = prefs[PreferencesKeys.BACKGROUND_VIDEO_PATH] ?: defaults.backgroundVideoPath,
            backgroundOpacity = prefs[PreferencesKeys.BACKGROUND_OPACITY] ?: defaults.backgroundOpacity,
            videoPlaybackSpeed = prefs[PreferencesKeys.VIDEO_PLAYBACK_SPEED] ?: defaults.videoPlaybackSpeed,
            language = prefs[PreferencesKeys.LANGUAGE] ?: defaults.language,
            controlsOpacity = prefs[PreferencesKeys.CONTROLS_OPACITY] ?: defaults.controlsOpacity,
            vibrationEnabled = prefs[PreferencesKeys.VIBRATION_ENABLED] ?: defaults.vibrationEnabled,
            virtualControllerVibrationEnabled = prefs[PreferencesKeys.VIRTUAL_CONTROLLER_VIBRATION_ENABLED]
                ?: defaults.virtualControllerVibrationEnabled,
            virtualControllerVibrationIntensity = prefs[PreferencesKeys.VIRTUAL_CONTROLLER_VIBRATION_INTENSITY]
                ?: defaults.virtualControllerVibrationIntensity,
            virtualControllerAsFirst = prefs[PreferencesKeys.VIRTUAL_CONTROLLER_AS_FIRST] ?: defaults.virtualControllerAsFirst,
            backButtonOpenMenu = prefs[PreferencesKeys.BACK_BUTTON_OPEN_MENU] ?: defaults.backButtonOpenMenu,
            touchMultitouchEnabled = prefs[PreferencesKeys.TOUCH_MULTITOUCH_ENABLED] ?: defaults.touchMultitouchEnabled,
            fpsDisplayEnabled = prefs[PreferencesKeys.FPS_DISPLAY_ENABLED] ?: defaults.fpsDisplayEnabled,
            fpsDisplayX = prefs[PreferencesKeys.FPS_DISPLAY_X] ?: defaults.fpsDisplayX,
            fpsDisplayY = prefs[PreferencesKeys.FPS_DISPLAY_Y] ?: defaults.fpsDisplayY,
            keyboardType = KeyboardType.fromValue(prefs[PreferencesKeys.KEYBOARD_TYPE] ?: defaults.keyboardType.value),
            touchEventEnabled = prefs[PreferencesKeys.TOUCH_EVENT_ENABLED] ?: defaults.touchEventEnabled,
            mouseRightStickEnabled = prefs[PreferencesKeys.MOUSE_RIGHT_STICK_ENABLED] ?: defaults.mouseRightStickEnabled,
            mouseRightStickAttackMode = prefs[PreferencesKeys.MOUSE_RIGHT_STICK_ATTACK_MODE] ?: defaults.mouseRightStickAttackMode,
            mouseRightStickSpeed = prefs[PreferencesKeys.MOUSE_RIGHT_STICK_SPEED] ?: defaults.mouseRightStickSpeed,
            mouseRightStickRangeLeft = prefs[PreferencesKeys.MOUSE_RIGHT_STICK_RANGE_LEFT] ?: defaults.mouseRightStickRangeLeft,
            mouseRightStickRangeTop = prefs[PreferencesKeys.MOUSE_RIGHT_STICK_RANGE_TOP] ?: defaults.mouseRightStickRangeTop,
            mouseRightStickRangeRight = prefs[PreferencesKeys.MOUSE_RIGHT_STICK_RANGE_RIGHT] ?: defaults.mouseRightStickRangeRight,
            mouseRightStickRangeBottom = prefs[PreferencesKeys.MOUSE_RIGHT_STICK_RANGE_BOTTOM] ?: defaults.mouseRightStickRangeBottom,
            logSystemEnabled = prefs[PreferencesKeys.LOG_SYSTEM_ENABLED] ?: defaults.logSystemEnabled,
            verboseLogging = prefs[PreferencesKeys.VERBOSE_LOGGING] ?: defaults.verboseLogging,
            setThreadAffinityToBigCore = prefs[PreferencesKeys.SET_THREAD_AFFINITY_TO_BIG_CORE] ?: defaults.setThreadAffinityToBigCore,
            fnaRenderer = normalizedRenderer,
            fnaMapBufferRangeOptimization = prefs[PreferencesKeys.FNA_MAP_BUFFER_RANGE_OPTIMIZATION]
                ?: defaults.fnaMapBufferRangeOptimization,
            qualityLevel = prefs[PreferencesKeys.FNA_QUALITY_LEVEL] ?: defaults.qualityLevel,
            shaderLowPrecision = prefs[PreferencesKeys.FNA_SHADER_LOW_PRECISION] ?: defaults.shaderLowPrecision,
            targetFps = prefs[PreferencesKeys.FNA_TARGET_FPS] ?: defaults.targetFps,
            serverGC = prefs[PreferencesKeys.SERVER_GC] ?: defaults.serverGC,
            concurrentGC = prefs[PreferencesKeys.CONCURRENT_GC] ?: defaults.concurrentGC,
            gcHeapCount = prefs[PreferencesKeys.GC_HEAP_COUNT] ?: defaults.gcHeapCount,
            tieredCompilation = prefs[PreferencesKeys.TIERED_COMPILATION] ?: defaults.tieredCompilation,
            quickJIT = prefs[PreferencesKeys.QUICK_JIT] ?: defaults.quickJIT,
            jitOptimizeType = prefs[PreferencesKeys.JIT_OPTIMIZE_TYPE] ?: defaults.jitOptimizeType,
            retainVM = prefs[PreferencesKeys.RETAIN_VM] ?: defaults.retainVM,
            killLauncherUIAfterLaunch = prefs[PreferencesKeys.KILL_LAUNCHER_UI_AFTER_LAUNCH]
                ?: defaults.killLauncherUIAfterLaunch,
            sdlAaudioLowLatency = prefs[PreferencesKeys.SDL_AAUDIO_LOW_LATENCY] ?: defaults.sdlAaudioLowLatency,
            box64Enabled = prefs[PreferencesKeys.BOX64_ENABLED] ?: defaults.box64Enabled,
            box64GamePath = prefs[PreferencesKeys.BOX64_GAME_PATH] ?: defaults.box64GamePath,
        )
    }

    private fun writeAppSettings(prefs: MutablePreferences, settings: AppSettings) {
        prefs[PreferencesKeys.THEME_MODE] = settings.themeMode.value
        prefs[PreferencesKeys.THEME_COLOR] = settings.themeColor
        prefs[PreferencesKeys.BACKGROUND_TYPE] = settings.backgroundType.value
        prefs[PreferencesKeys.BACKGROUND_COLOR] = settings.backgroundColor
        prefs[PreferencesKeys.BACKGROUND_IMAGE_PATH] = settings.backgroundImagePath
        prefs[PreferencesKeys.BACKGROUND_VIDEO_PATH] = settings.backgroundVideoPath
        prefs[PreferencesKeys.BACKGROUND_OPACITY] = settings.backgroundOpacity
        prefs[PreferencesKeys.VIDEO_PLAYBACK_SPEED] = settings.videoPlaybackSpeed
        prefs[PreferencesKeys.LANGUAGE] = settings.language
        prefs[PreferencesKeys.CONTROLS_OPACITY] = settings.controlsOpacity
        prefs[PreferencesKeys.VIBRATION_ENABLED] = settings.vibrationEnabled
        prefs[PreferencesKeys.VIRTUAL_CONTROLLER_VIBRATION_ENABLED] = settings.virtualControllerVibrationEnabled
        prefs[PreferencesKeys.VIRTUAL_CONTROLLER_VIBRATION_INTENSITY] = settings.virtualControllerVibrationIntensity
        prefs[PreferencesKeys.VIRTUAL_CONTROLLER_AS_FIRST] = settings.virtualControllerAsFirst
        prefs[PreferencesKeys.BACK_BUTTON_OPEN_MENU] = settings.backButtonOpenMenu
        prefs[PreferencesKeys.TOUCH_MULTITOUCH_ENABLED] = settings.touchMultitouchEnabled
        prefs[PreferencesKeys.FPS_DISPLAY_ENABLED] = settings.fpsDisplayEnabled
        prefs[PreferencesKeys.FPS_DISPLAY_X] = settings.fpsDisplayX
        prefs[PreferencesKeys.FPS_DISPLAY_Y] = settings.fpsDisplayY
        prefs[PreferencesKeys.KEYBOARD_TYPE] = settings.keyboardType.value
        prefs[PreferencesKeys.TOUCH_EVENT_ENABLED] = settings.touchEventEnabled
        prefs[PreferencesKeys.MOUSE_RIGHT_STICK_ENABLED] = settings.mouseRightStickEnabled
        prefs[PreferencesKeys.MOUSE_RIGHT_STICK_ATTACK_MODE] = settings.mouseRightStickAttackMode
        prefs[PreferencesKeys.MOUSE_RIGHT_STICK_SPEED] = settings.mouseRightStickSpeed
        prefs[PreferencesKeys.MOUSE_RIGHT_STICK_RANGE_LEFT] = settings.mouseRightStickRangeLeft
        prefs[PreferencesKeys.MOUSE_RIGHT_STICK_RANGE_TOP] = settings.mouseRightStickRangeTop
        prefs[PreferencesKeys.MOUSE_RIGHT_STICK_RANGE_RIGHT] = settings.mouseRightStickRangeRight
        prefs[PreferencesKeys.MOUSE_RIGHT_STICK_RANGE_BOTTOM] = settings.mouseRightStickRangeBottom
        prefs[PreferencesKeys.LOG_SYSTEM_ENABLED] = settings.logSystemEnabled
        prefs[PreferencesKeys.VERBOSE_LOGGING] = settings.verboseLogging
        prefs[PreferencesKeys.SET_THREAD_AFFINITY_TO_BIG_CORE] = settings.setThreadAffinityToBigCore
        prefs[PreferencesKeys.FNA_RENDERER] = normalizeRendererValue(settings.fnaRenderer)
        prefs[PreferencesKeys.FNA_MAP_BUFFER_RANGE_OPTIMIZATION] = settings.fnaMapBufferRangeOptimization
        prefs[PreferencesKeys.FNA_QUALITY_LEVEL] = settings.qualityLevel
        prefs[PreferencesKeys.FNA_SHADER_LOW_PRECISION] = settings.shaderLowPrecision
        prefs[PreferencesKeys.FNA_TARGET_FPS] = settings.targetFps
        prefs[PreferencesKeys.SERVER_GC] = settings.serverGC
        prefs[PreferencesKeys.CONCURRENT_GC] = settings.concurrentGC
        prefs[PreferencesKeys.GC_HEAP_COUNT] = settings.gcHeapCount
        prefs[PreferencesKeys.TIERED_COMPILATION] = settings.tieredCompilation
        prefs[PreferencesKeys.QUICK_JIT] = settings.quickJIT
        prefs[PreferencesKeys.JIT_OPTIMIZE_TYPE] = settings.jitOptimizeType
        prefs[PreferencesKeys.RETAIN_VM] = settings.retainVM
        prefs[PreferencesKeys.KILL_LAUNCHER_UI_AFTER_LAUNCH] = settings.killLauncherUIAfterLaunch
        prefs[PreferencesKeys.SDL_AAUDIO_LOW_LATENCY] = settings.sdlAaudioLowLatency
        prefs[PreferencesKeys.BOX64_ENABLED] = settings.box64Enabled
        prefs[PreferencesKeys.BOX64_GAME_PATH] = settings.box64GamePath
    }

    private fun normalizeRendererValue(value: String?): String {
        if (value.isNullOrBlank()) return FnaRenderer.AUTO.value
        return when (value.lowercase()) {
            "自动", "自动选择", "auto" -> FnaRenderer.AUTO.value
            "native", "native opengl es 3", "opengl", "opengl es", "opengles3", "opengl_native" -> FnaRenderer.NATIVE.value
            "gl4es", "opengl_gl4es" -> FnaRenderer.GL4ES.value
            "gl4es+angle" -> FnaRenderer.GL4ES_ANGLE.value
            "mobileglues" -> FnaRenderer.MOBILEGLUES.value
            "angle" -> FnaRenderer.ANGLE.value
            "zink", "vulkan" -> FnaRenderer.ZINK.value
            else -> value
        }
    }
}
