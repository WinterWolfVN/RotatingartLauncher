package com.app.ralaunch.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.app.ralaunch.data.SettingsManager
import com.app.ralaunch.patch.PatchManager
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.shared.ui.components.dialogs.*
import com.app.ralaunch.shared.ui.screens.settings.*
import com.app.ralaunch.shared.ui.theme.AppThemeState
import com.app.ralaunch.sponsor.SponsorsActivity
import com.app.ralaunch.ui.compose.dialogs.PatchManagementDialogCompose
import com.app.ralaunch.ui.compose.settings.SettingsViewModel as AppSettingsViewModel
import com.app.ralaunch.ui.main.MainActivityCompose
import com.app.ralaunch.utils.AppLogger
import com.app.ralaunch.utils.LocaleManager
import com.app.ralaunch.utils.LogcatReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 设置页面包装器 - App 层
 * 
 * 整合所有设置功能，处理 Android 特定交互
 */
@Composable
fun SettingsScreenWrapper(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val viewModel = remember { AppSettingsViewModel(context) }
    val uiState by viewModel.uiState.collectAsState()
    val effect by viewModel.effect.collectAsState()

    // 对话框状态
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeColorDialog by remember { mutableStateOf(false) }
    var showRendererDialog by remember { mutableStateOf(false) }
    var showLogViewerDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showPatchManagementDialog by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }

    // 文件选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { 
            scope.launch {
                handleImageSelection(context, it, viewModel)
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { 
            scope.launch {
                handleVideoSelection(context, it, viewModel)
            }
        }
    }

    val logExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            scope.launch {
                exportLogs(context, it)
            }
        }
    }

    // 处理副作用
    LaunchedEffect(effect) {
        effect?.let { eff ->
            when (eff) {
                is SettingsEffect.OpenImagePicker -> imagePickerLauncher.launch("image/*")
                is SettingsEffect.OpenVideoPicker -> videoPickerLauncher.launch("video/*")
                is SettingsEffect.OpenUrl -> openUrl(context, eff.url)
                is SettingsEffect.ShowToast -> Toast.makeText(context, eff.message, Toast.LENGTH_SHORT).show()
                is SettingsEffect.OpenLanguageDialog -> showLanguageDialog = true
                is SettingsEffect.OpenThemeColorDialog -> showThemeColorDialog = true
                is SettingsEffect.OpenRendererDialog -> showRendererDialog = true
                is SettingsEffect.ViewLogsPage -> {
                    logs = loadLogs(context)
                    showLogViewerDialog = true
                }
                is SettingsEffect.ExportLogsToFile -> {
                    logExportLauncher.launch("ralaunch_logs_${System.currentTimeMillis()}.txt")
                }
                is SettingsEffect.OpenLicensePage -> showLicenseDialog = true
                is SettingsEffect.OpenSponsorsPage -> openSponsorsPage(context)
                is SettingsEffect.OpenPatchManagementDialog -> {
                    showPatchManagementDialog = true
                }
                is SettingsEffect.ClearCacheComplete -> clearAppCache(context)
                is SettingsEffect.ForceReinstallPatchesComplete -> forceReinstallPatches(context)
                is SettingsEffect.BackgroundOpacityChanged -> applyOpacityChange(context, eff.opacity)
                is SettingsEffect.VideoSpeedChanged -> applyVideoSpeedChange(context, eff.speed)
                is SettingsEffect.RestoreDefaultBackgroundComplete -> restoreDefaultBackground(context)
            }
            viewModel.clearEffect()
        }
    }

    // 渲染设置页面
    SettingsScreenContent(
        currentCategory = uiState.currentCategory,
        onCategoryClick = { viewModel.selectCategory(it) }
    ) { category ->
        when (category) {
            SettingsCategory.APPEARANCE -> {
                AppearanceSettingsContent(
                    state = AppearanceState(
                        themeMode = uiState.themeMode,
                        themeColor = uiState.themeColor,
                        backgroundType = uiState.backgroundType,
                        backgroundOpacity = uiState.backgroundOpacity,
                        videoPlaybackSpeed = uiState.videoPlaybackSpeed,
                        language = uiState.language
                    ),
                    onThemeModeChange = { viewModel.setThemeMode(it) },
                    onThemeColorClick = { viewModel.onEvent(SettingsEvent.OpenThemeColorSelector) },
                    onBackgroundTypeChange = { viewModel.setBackgroundType(it) },
                    onSelectImageClick = { viewModel.onEvent(SettingsEvent.SelectBackgroundImage) },
                    onSelectVideoClick = { viewModel.onEvent(SettingsEvent.SelectBackgroundVideo) },
                    onBackgroundOpacityChange = { viewModel.onEvent(SettingsEvent.SetBackgroundOpacity(it)) },
                    onVideoSpeedChange = { viewModel.onEvent(SettingsEvent.SetVideoPlaybackSpeed(it)) },
                    onRestoreDefaultBackground = { viewModel.onEvent(SettingsEvent.RestoreDefaultBackground) },
                    onLanguageClick = { viewModel.onEvent(SettingsEvent.OpenLanguageSelector) }
                )
            }
            SettingsCategory.CONTROLS -> {
                ControlsSettingsContent(
                    touchMultitouchEnabled = uiState.touchMultitouchEnabled,
                    onTouchMultitouchChange = { viewModel.setTouchMultitouch(it) },
                    mouseRightStickEnabled = uiState.mouseRightStickEnabled,
                    onMouseRightStickChange = { viewModel.setMouseRightStick(it) },
                    vibrationEnabled = uiState.vibrationEnabled,
                    onVibrationChange = { viewModel.setVibrationEnabled(it) },
                    vibrationStrength = uiState.vibrationStrength,
                    onVibrationStrengthChange = { viewModel.setVibrationStrength(it) }
                )
            }
            SettingsCategory.GAME -> {
                GameSettingsContent(
                    bigCoreAffinityEnabled = uiState.bigCoreAffinityEnabled,
                    onBigCoreAffinityChange = { viewModel.setBigCoreAffinity(it) },
                    lowLatencyAudioEnabled = uiState.lowLatencyAudioEnabled,
                    onLowLatencyAudioChange = { viewModel.setLowLatencyAudio(it) },
                    rendererType = uiState.rendererType,
                    onRendererClick = { viewModel.onEvent(SettingsEvent.OpenRendererSelector) },
                    vulkanTurnipEnabled = uiState.vulkanTurnipEnabled,
                    onVulkanTurnipChange = { viewModel.onEvent(SettingsEvent.SetVulkanTurnip(it)) },
                    isAdrenoGpu = uiState.isAdrenoGpu
                )
            }
            SettingsCategory.LAUNCHER -> {
                LauncherSettingsContent(
                    onPatchManagementClick = { viewModel.onEvent(SettingsEvent.OpenPatchManagement) },
                    onForceReinstallPatchesClick = { viewModel.onEvent(SettingsEvent.ForceReinstallPatches) }
                )
            }
            SettingsCategory.DEVELOPER -> {
                DeveloperSettingsContent(
                    state = DeveloperState(
                        loggingEnabled = uiState.loggingEnabled,
                        verboseLogging = uiState.verboseLogging,
                        bigCoreAffinityEnabled = uiState.bigCoreAffinityEnabled,
                        killLauncherUIEnabled = uiState.killLauncherUIEnabled,
                        lowLatencyAudioEnabled = uiState.lowLatencyAudioEnabled,
                        serverGCEnabled = uiState.serverGCEnabled,
                        concurrentGCEnabled = uiState.concurrentGCEnabled,
                        tieredCompilationEnabled = uiState.tieredCompilationEnabled,
                        fnaMapBufferRangeOptEnabled = uiState.fnaMapBufferRangeOptEnabled
                    ),
                    onLoggingChange = { viewModel.setLoggingEnabled(it) },
                    onVerboseLoggingChange = { viewModel.onEvent(SettingsEvent.SetVerboseLogging(it)) },
                    onViewLogsClick = { viewModel.onEvent(SettingsEvent.ViewLogs) },
                    onExportLogsClick = { viewModel.onEvent(SettingsEvent.ExportLogs) },
                    onClearCacheClick = { viewModel.onEvent(SettingsEvent.ClearCache) },
                    onBigCoreAffinityChange = { viewModel.setBigCoreAffinity(it) },
                    onKillLauncherUIChange = { viewModel.onEvent(SettingsEvent.SetKillLauncherUI(it)) },
                    onLowLatencyAudioChange = { viewModel.setLowLatencyAudio(it) },
                    onServerGCChange = { viewModel.onEvent(SettingsEvent.SetServerGC(it)) },
                    onConcurrentGCChange = { viewModel.onEvent(SettingsEvent.SetConcurrentGC(it)) },
                    onTieredCompilationChange = { viewModel.onEvent(SettingsEvent.SetTieredCompilation(it)) },
                    onFnaMapBufferRangeOptChange = { viewModel.onEvent(SettingsEvent.SetFnaMapBufferRangeOpt(it)) },
                    onForceReinstallPatchesClick = { viewModel.onEvent(SettingsEvent.ForceReinstallPatches) }
                )
            }
            SettingsCategory.ABOUT -> {
                AboutSettingsContent(
                    state = AboutState(
                        appVersion = uiState.appVersion,
                        buildInfo = uiState.buildInfo
                    ),
                    onCheckUpdateClick = { viewModel.onEvent(SettingsEvent.CheckUpdate) },
                    onLicenseClick = { viewModel.onEvent(SettingsEvent.OpenLicense) },
                    onSponsorsClick = { viewModel.onEvent(SettingsEvent.OpenSponsors) },
                    onCommunityLinkClick = { url -> viewModel.onEvent(SettingsEvent.OpenUrl(url)) },
                    onContributorClick = { url -> viewModel.onEvent(SettingsEvent.OpenUrl(url)) }
                )
            }
        }
    }

    // ==================== 对话框 ====================

    if (showLanguageDialog) {
        LanguageSelectDialog(
            currentLanguage = getLanguageCode(uiState.language),
            onSelect = { code ->
                LocaleManager.setLanguage(context, code)
                viewModel.onEvent(SettingsEvent.SetLanguage(code))
                Toast.makeText(context, "语言已更改，重启后生效", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showThemeColorDialog) {
        ThemeColorSelectDialog(
            currentColor = uiState.themeColor,
            onSelect = { color ->
                // 保存到设置
                SettingsManager.getInstance().themeColor = color
                // 更新 ViewModel
                viewModel.onEvent(SettingsEvent.SetThemeColor(color))
                // 更新全局主题状态
                AppThemeState.updateThemeColor(color)
            },
            onDismiss = { showThemeColorDialog = false }
        )
    }

    if (showRendererDialog) {
        // 获取设备上实际可用的渲染器
        val availableRenderers = remember {
            val compatible = com.app.ralaunch.renderer.RendererConfig.getCompatibleRenderers(context)
            // 始终包含 "自动选择" 选项
            val list = mutableListOf(
                RendererOption("auto", "自动选择", "根据设备自动选择最佳渲染器")
            )
            compatible.forEach { info ->
                list.add(RendererOption(info.id, info.displayName ?: info.id, info.description ?: ""))
            }
            list
        }
        
        RendererSelectDialog(
            currentRenderer = getRendererCode(uiState.rendererType),
            renderers = availableRenderers,
            onSelect = { rendererId ->
                viewModel.onEvent(SettingsEvent.SetRenderer(rendererId))
            },
            onDismiss = { showRendererDialog = false }
        )
    }

    if (showLogViewerDialog) {
        LogViewerDialog(
            logs = logs,
            onExport = {
                logExportLauncher.launch("ralaunch_logs_${System.currentTimeMillis()}.txt")
            },
            onClear = {
                clearLogs(context)
                logs = emptyList()
                Toast.makeText(context, "日志已清除", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showLogViewerDialog = false }
        )
    }

    if (showLicenseDialog) {
        LicenseDialog(onDismiss = { showLicenseDialog = false })
    }
    
    if (showPatchManagementDialog) {
        PatchManagementDialogCompose(
            onDismiss = { showPatchManagementDialog = false }
        )
    }
}

// ==================== 辅助函数 ====================

private suspend fun handleImageSelection(context: Context, uri: Uri, viewModel: AppSettingsViewModel) {
    withContext(Dispatchers.IO) {
        try {
            val backgroundDir = File(context.filesDir, "backgrounds")
            if (!backgroundDir.exists()) backgroundDir.mkdirs()
            
            val destFile = File(backgroundDir, "background_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // 删除旧背景
            val oldPath = SettingsManager.getInstance().backgroundImagePath
            if (!oldPath.isNullOrEmpty()) {
                val oldFile = File(oldPath)
                if (oldFile.exists() && oldFile.parentFile == backgroundDir) {
                    oldFile.delete()
                }
            }
            
            val newPath = destFile.absolutePath
            SettingsManager.getInstance().apply {
                backgroundImagePath = newPath
                backgroundType = "image"
                backgroundVideoPath = ""
                backgroundOpacity = 90
            }
            
            withContext(Dispatchers.Main) {
                // 实时更新全局主题状态
                AppThemeState.updateBackgroundType(1)
                AppThemeState.updateBackgroundImagePath(newPath)
                AppThemeState.updateBackgroundVideoPath("")
                AppThemeState.updateBackgroundOpacity(90)
                
                viewModel.setBackgroundType(1)
                viewModel.onEvent(SettingsEvent.SetBackgroundOpacity(90))
                Toast.makeText(context, "背景图片已设置", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "设置背景失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private suspend fun handleVideoSelection(context: Context, uri: Uri, viewModel: AppSettingsViewModel) {
    withContext(Dispatchers.IO) {
        try {
            val backgroundDir = File(context.filesDir, "backgrounds")
            if (!backgroundDir.exists()) backgroundDir.mkdirs()
            
            val destFile = File(backgroundDir, "background_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            val newPath = destFile.absolutePath
            SettingsManager.getInstance().apply {
                backgroundVideoPath = newPath
                backgroundType = "video"
                backgroundImagePath = ""
                backgroundOpacity = 90
            }
            
            withContext(Dispatchers.Main) {
                // 实时更新全局主题状态
                AppThemeState.updateBackgroundType(2)
                AppThemeState.updateBackgroundVideoPath(newPath)
                AppThemeState.updateBackgroundImagePath("")
                AppThemeState.updateBackgroundOpacity(90)
                
                viewModel.setBackgroundType(2)
                viewModel.onEvent(SettingsEvent.SetBackgroundOpacity(90))
                Toast.makeText(context, "背景视频已设置", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "设置背景失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}

private fun openSponsorsPage(context: Context) {
    try {
        context.startActivity(Intent(context, SponsorsActivity::class.java))
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开赞助商页面", Toast.LENGTH_SHORT).show()
    }
}

private fun loadLogs(context: Context): List<String> {
    return try {
        LogcatReader.getInstance()?.logFile?.readLines()?.takeLast(500) ?: emptyList()
    } catch (e: Exception) {
        listOf("无法读取日志: ${e.message}")
    }
}

private fun clearLogs(context: Context) {
    try {
        LogcatReader.getInstance()?.logFile?.writeText("")
    } catch (e: Exception) {
        AppLogger.error("Settings", "清除日志失败", e)
    }
}

private suspend fun exportLogs(context: Context, uri: Uri) {
    withContext(Dispatchers.IO) {
        try {
            val logs = loadLogs(context)
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(logs.joinToString("\n").toByteArray())
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "日志已导出", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun clearAppCache(context: Context) {
    try {
        context.cacheDir.deleteRecursively()
        context.externalCacheDir?.deleteRecursively()
        Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "清除缓存失败", Toast.LENGTH_SHORT).show()
    }
}

private fun forceReinstallPatches(context: Context) {
    Thread {
        try {
            val patchManager: PatchManager? = try {
                KoinJavaComponent.getOrNull(PatchManager::class.java)
            } catch (e: Exception) { null }
            patchManager?.let { pm ->
                PatchManager.installBuiltInPatches(pm, true)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "补丁已重新安装", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "重装补丁失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }.start()
}

private fun applyOpacityChange(context: Context, opacity: Int) {
    SettingsManager.getInstance().backgroundOpacity = opacity
    MainActivityCompose.instance?.updateVideoBackgroundOpacity(opacity)
}

private fun applyVideoSpeedChange(context: Context, speed: Float) {
    SettingsManager.getInstance().videoPlaybackSpeed = speed
    MainActivityCompose.instance?.updateVideoBackgroundSpeed(speed)
}

private fun restoreDefaultBackground(context: Context) {
    SettingsManager.getInstance().apply {
        backgroundType = "default"
        backgroundImagePath = ""
        backgroundVideoPath = ""
        backgroundOpacity = 0
        videoPlaybackSpeed = 1.0f
    }
    MainActivityCompose.instance?.updateVideoBackground()
}

private fun applyThemeColor(context: Context, colorId: Int) {
    SettingsManager.getInstance().themeColor = colorId
    AppThemeState.updateThemeColor(colorId)
    Toast.makeText(context, "主题颜色已更改", Toast.LENGTH_SHORT).show()
}

private fun getLanguageCode(languageName: String): String {
    return when (languageName) {
        "简体中文" -> LocaleManager.LANGUAGE_ZH
        "English" -> LocaleManager.LANGUAGE_EN
        "Русский" -> LocaleManager.LANGUAGE_RU
        "Español" -> LocaleManager.LANGUAGE_ES
        else -> LocaleManager.LANGUAGE_AUTO
    }
}

private fun getRendererCode(rendererName: String): String {
    return when (rendererName) {
        "自动选择" -> "auto"
        "Native OpenGL ES 3" -> "native"
        "GL4ES" -> "gl4es"
        "GL4ES + ANGLE" -> "gl4es+angle"
        "MobileGlues" -> "mobileglues"
        "ANGLE" -> "angle"
        "DXVK" -> "dxvk"
        else -> "auto"
    }
}

// ==================== 控制和游戏设置内容 ====================

@Composable
private fun ControlsSettingsContent(
    touchMultitouchEnabled: Boolean,
    onTouchMultitouchChange: (Boolean) -> Unit,
    mouseRightStickEnabled: Boolean,
    onMouseRightStickChange: (Boolean) -> Unit,
    vibrationEnabled: Boolean,
    onVibrationChange: (Boolean) -> Unit,
    vibrationStrength: Float,
    onVibrationStrengthChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSection(title = "触控") {
            SwitchSettingItem(
                title = "多点触控",
                subtitle = "允许同时多个触控点",
                checked = touchMultitouchEnabled,
                onCheckedChange = onTouchMultitouchChange
            )

            SettingsDivider()

            SwitchSettingItem(
                title = "鼠标右摇杆",
                subtitle = "鼠标移动映射到右摇杆",
                checked = mouseRightStickEnabled,
                onCheckedChange = onMouseRightStickChange
            )
        }

        SettingsSection(title = "震动") {
            SwitchSettingItem(
                title = "启用震动",
                subtitle = "控制器震动反馈",
                checked = vibrationEnabled,
                onCheckedChange = onVibrationChange
            )

            if (vibrationEnabled) {
                SettingsDivider()

                SliderSettingItem(
                    title = "震动强度",
                    value = vibrationStrength,
                    valueRange = 0f..1f,
                    valueLabel = "${(vibrationStrength * 100).toInt()}%",
                    onValueChange = onVibrationStrengthChange
                )
            }
        }
    }
}

@Composable
private fun GameSettingsContent(
    bigCoreAffinityEnabled: Boolean,
    onBigCoreAffinityChange: (Boolean) -> Unit,
    lowLatencyAudioEnabled: Boolean,
    onLowLatencyAudioChange: (Boolean) -> Unit,
    rendererType: String,
    onRendererClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSection(title = "性能") {
            SwitchSettingItem(
                title = "大核心亲和性",
                subtitle = "将游戏线程绑定到高性能核心",
                checked = bigCoreAffinityEnabled,
                onCheckedChange = onBigCoreAffinityChange
            )

            SettingsDivider()

            SwitchSettingItem(
                title = "低延迟音频",
                subtitle = "降低音频延迟，可能增加功耗",
                checked = lowLatencyAudioEnabled,
                onCheckedChange = onLowLatencyAudioChange
            )
        }

        SettingsSection(title = "渲染") {
            ClickableSettingItem(
                title = "渲染器",
                subtitle = "选择图形渲染后端",
                value = rendererType,
                onClick = onRendererClick
            )
        }
    }
}
